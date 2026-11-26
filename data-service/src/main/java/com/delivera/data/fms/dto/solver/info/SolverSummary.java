package com.delivera.data.fms.dto.solver.info;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolverSummary {

    private  TypeSolver solver;

    private boolean own;

    private boolean active;

    private List<SolverParameter> parameters;

}
