package com.cani.service;

import com.cani.entity.Worker;
import com.cani.entity.WorkerGroup;
import java.util.List;

record GroupCandidate(
        WorkerGroup group,
        Worker leader,
        List<Worker> eligible,
        double score,
        double medianDist,
        double p90Dist,
        double maxDist,
        int packSize
) {
}
