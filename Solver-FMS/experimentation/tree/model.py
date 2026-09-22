"""
Decision tree model for solver selection.
"""

from collections import Counter

import numpy as np
from sklearn.dummy import DummyClassifier
from sklearn.model_selection import (LeaveOneOut, StratifiedKFold, cross_val_score,
                                     permutation_test_score)
from sklearn.tree import DecisionTreeClassifier, export_text

from .dataset import FEATURES


def make_tree(config):
    return DecisionTreeClassifier(
        max_depth=config.max_depth,
        min_samples_leaf=config.min_leaf,
        random_state=config.seed,
        class_weight="balanced",
    )


def tree_text(tree, classes):
    """
    El arbol en texto.

    Con una sola clase lo escribimos a mano: sklearn ignora `class_names` en ese caso y
    saca `class: 0`, el indice en vez del nombre, que no dice nada al leerlo.
    """
    if len(classes) < 2:
        return f"|--- class: {next(iter(classes))}\n"
    return export_text(tree, feature_names=FEATURES, class_names=list(tree.classes_), decimals=2)


def train(dataset, config):
    """
    Entrena el arbol de decision y devuelve un diccionario con el modelo y sus caracteristicas.
    """
    labels = [row["winner"] for row in dataset]
    classes = Counter(labels)

    X = np.array([[row[f] for f in FEATURES] for row in dataset], dtype=float)
    y = np.array(labels)
    tree = make_tree(config)

    result = {"classes": classes, "model": tree, "validated": len(classes) >= 2}

    if result["validated"]:
        # Tantas particiones como permita la clase mas pequeña: pedir 5 con una clase de 3
        # deja pliegues sin representar y sklearn devuelve avisos y NaN.
        folds = max(2, min(config.folds, min(classes.values())))
        splitter = StratifiedKFold(n_splits=folds, shuffle=True, random_state=config.seed)

        scores = cross_val_score(tree, X, y, cv=splitter, scoring="accuracy")
        baseline = cross_val_score(DummyClassifier(strategy="most_frequent"), X, y,
                                   cv=splitter, scoring="accuracy")
        _, _, p_value = permutation_test_score(
            tree, X, y, cv=splitter, scoring="accuracy",
            n_permutations=config.permutations, random_state=config.seed, n_jobs=None)

        result.update({
            "folds": folds,
            "accuracy": float(scores.mean()), "accuracy_sd": float(scores.std()),
            "baseline": float(baseline.mean()), "p_value": float(p_value),
        })

    tree.fit(X, y)
    result.update({
        "importances": [(name, float(value))
                        for name, value in sorted(zip(FEATURES, tree.feature_importances_),
                                                  key=lambda kv: -kv[1]) if value > 0],
        "text": tree_text(tree, classes),
        "predictions": list(tree.predict(X)),
    })
    return result

def selftest(config):
    """
    Comprueba que el arbol de decision funciona como se espera en un caso sencillo.
    """
    rng = np.random.default_rng(config.seed)
    n = 120
    signal = rng.uniform(0, 300, n)
    X = np.column_stack([signal, rng.normal(0, 1, n), rng.uniform(0, 100, n)])
    y = np.where(signal > 150, "ALFA", "BETA")

    tree = DecisionTreeClassifier(max_depth=config.max_depth, min_samples_leaf=config.min_leaf,
                                  random_state=config.seed)
    tree.fit(X, y)
    root_feature = int(tree.tree_.feature[0])
    threshold = float(tree.tree_.threshold[0])
    accuracy = cross_val_score(tree, X, y, cv=LeaveOneOut(), scoring="accuracy").mean()

    return {
        "feature_index": root_feature,
        "feature_ok": root_feature == 0,
        "threshold": threshold,
        "accuracy": float(accuracy),
        "passed": root_feature == 0 and abs(threshold - 150) < 15 and accuracy > 0.95,
    }
