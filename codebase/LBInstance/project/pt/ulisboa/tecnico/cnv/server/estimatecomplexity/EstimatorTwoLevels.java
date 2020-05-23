package pt.ulisboa.tecnico.cnv.server.estimatecomplexity;

import java.util.HashMap;

public class EstimatorTwoLevels extends Estimator {

	private final HashMap<Integer, SizeLevelEstimator> sizeLevelEstimators = new HashMap<>();

	public EstimatorTwoLevels(String solver,  LinearRegression timeTransformRegression, LinearRegression heuristicLinearRegression) {
		super(solver, timeTransformRegression, heuristicLinearRegression);
	}

	@Override
	public Integer estimate(Integer size, Integer un) {
		Integer estimation;
		SizeLevelEstimator sizeLevelEstimator = sizeLevelEstimators.get(size);
		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		LinearRegression heuristicLinearRegression = getHeuristicLinearRegression();

		if (sizeLevelEstimator != null && sizeLevelEstimator.isValid(5)) {
			estimation = sizeLevelEstimator.estimate(5, un);
		} else if (calculatedLinearRegression.isValid(10)) {
			estimation = calculatedLinearRegression.estimate(un);
		} else {
			estimation = heuristicLinearRegression.estimate(un);
		}
		return transform(estimation);
	}

	public void setSizeLevelHeuristicRegression(Integer size, LinearRegression linearRegression) {
		SizeLevelEstimator sizeLevelEstimator = sizeLevelEstimators.get(size);
		if (sizeLevelEstimator == null) {
			sizeLevelEstimator = new SizeLevelEstimator(size);
			sizeLevelEstimators.put(size, sizeLevelEstimator);
		}
		sizeLevelEstimator.setHeuristicLinearRegression(linearRegression);
	}

	@Override
	public void addDataPoint(Integer size, Integer un, Integer methods) {
		SizeLevelEstimator sizeLevelEstimator = sizeLevelEstimators.get(size);
		if (sizeLevelEstimator == null) {
			sizeLevelEstimator = new SizeLevelEstimator(size);
			sizeLevelEstimators.put(size, sizeLevelEstimator);
		}
		LinearRegression sizeLevelEstimatorCalculatedLinearRegression = sizeLevelEstimator.getCalculatedLinearRegression();
		sizeLevelEstimatorCalculatedLinearRegression.addDataPoint(un, methods);

		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		calculatedLinearRegression.addDataPoint(un, methods);
	}

	@Override
	public void estimateCoefficients(Integer size) {
		SizeLevelEstimator sizeLevelEstimator = sizeLevelEstimators.get(size);
		if (sizeLevelEstimator != null) {
			LinearRegression sizeLevelEstimatorCalculatedLinearRegression = sizeLevelEstimator.getCalculatedLinearRegression();
			sizeLevelEstimatorCalculatedLinearRegression.estimateCoefficients();
		}
		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		calculatedLinearRegression.estimateCoefficients();
	}

	private static class SizeLevelEstimator {
		private final Integer size;
		private LinearRegression heuristicLinearRegression;
		private final LinearRegression calculatedLinearRegression;

		public SizeLevelEstimator(Integer size) {
			this.size = size;
			this.calculatedLinearRegression = new LinearRegression();
		}

		public Integer getSize() {
			return size;
		}

		public void setHeuristicLinearRegression(LinearRegression linearRegression) {
			this.heuristicLinearRegression = linearRegression;
		}

		public LinearRegression getCalculatedLinearRegression() {
			return calculatedLinearRegression;
		}

		public boolean isValid(Integer minSamples) {
			return calculatedLinearRegression.isValid(minSamples) || heuristicLinearRegression != null;
		}

		public Integer estimate(Integer minSamples, Integer un) {
			if (calculatedLinearRegression.isValid(minSamples)) return calculatedLinearRegression.estimate(un);
			else if (heuristicLinearRegression != null) return heuristicLinearRegression.estimate(un);
			else return -1;
		}
	}
}
