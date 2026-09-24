package com.satquery.service;

import com.satquery.entity.AnalysisType;
import com.satquery.entity.SatelliteImage;

import java.util.List;

/**
 * Decides which concrete analysis type to run when the client sends AnalysisType.AUTO.
 * The keyword based AnalysisRouter is the default; an LLM based strategy can replace it later.
 */
public interface AnalysisRoutingStrategy {

    AnalysisType route(String question, List<SatelliteImage> images);
}
