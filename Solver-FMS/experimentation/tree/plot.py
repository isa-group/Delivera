"""
El arbol como imagen.
"""

import matplotlib
matplotlib.use("Agg")  # Antes de importar pyplot: el backend se fija al importarlo.
import matplotlib.pyplot as plt  # noqa: E402  (el orden lo impone matplotlib)
from sklearn.tree import plot_tree  # noqa: E402

from .dataset import FEATURES  # noqa: E402



def render_tree(tree, path, config, dataset):
    """
    Dibuja el arbol de decision y lo guarda en path. Si es un nodo unico, se dibuja a mano
    para que diga lo que importa: que solver conviene, en cuantas instancias, y por cuanto le saca al segundo. 
    Sin ese margen la imagen afirma un ganador sin enseñar por que lo es.
    """
    n = len(dataset)
    if len(tree.classes_) < 2:
        _render_single_node(tree, path, dataset)
        return

    depth = max(1, tree.get_depth())
    figure, axes = plt.subplots(
        figsize=(max(9.0, 3.8 * tree.get_n_leaves()), 2.6 * (depth + 1)), dpi=300)
    plot_tree(tree, feature_names=FEATURES, class_names=list(tree.classes_), filled=True,
              rounded=True, impurity=False, fontsize=9, ax=axes)
    axes.set_title(f"Que solver conviene  ·  {n} instancias  ·  profundidad max {config.max_depth}",
                   fontsize=11, pad=14)
    figure.tight_layout()
    figure.savefig(path, bbox_inches="tight")
    plt.close(figure)


def _render_single_node(tree, path, dataset):
    """
    El arbol de un nodo, dibujado a mano para que diga lo que importa: que solver
    conviene, en cuantas instancias, y por cuanto le saca al segundo. Sin ese margen la
    imagen afirma un ganador sin enseñar por que lo es.
    """
    winner = str(tree.classes_[0])
    margins = [row["margin"] for row in dataset if row["margin"] != float("inf")]

    figure, axes = plt.subplots(figsize=(9.0, 3.2), dpi=300)
    axes.axis("off")
    axes.set_xlim(0, 1)
    axes.set_ylim(0, 1)

    axes.text(0.5, 0.88, f"Que solver conviene  ·  {len(dataset)} instancias",
              ha="center", va="center", fontsize=12)
    axes.text(0.5, 0.52, f"{winner}\n{len(dataset)} instancias",
              ha="center", va="center", fontsize=14, fontweight="bold", color="white",
              bbox=dict(boxstyle="round,pad=0.6", facecolor="#2b7bba", edgecolor="none"))

    if margins:
        detail = (f"Gana en todas. El segundo mejor queda entre "
                  f"+{min(margins):.1f} % y +{max(margins):.1f} % por encima en coste.")
    else:
        detail = "Unico solver en los datos: no hay con quien compararlo."
    axes.text(0.5, 0.14, detail, ha="center", va="center", fontsize=10, color="#444444")

    figure.tight_layout()
    figure.savefig(path, bbox_inches="tight")
    plt.close(figure)
