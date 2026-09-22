package com.delivera.client.transaction.compensation;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CompensationAction {

    private Runnable rollback;

    private Runnable rollbackFailure;

}
