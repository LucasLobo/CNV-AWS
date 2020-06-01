package pt.ulisboa.tecnico.cnv.estimatecomplexity;

public class EstimatorDLX extends Estimator {

	public EstimatorDLX() {
		super("DLX",
				new LinearRegression(0.61f,7222f,100f),
				new LinearRegression(1690.71f, -12737f, 94.80f));
	}

	@Override
	public Integer estimate(Integer size, Integer un) {
		Integer estimation;
		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		LinearRegression heuristicLinearRegression = getHeuristicLinearRegression();
		if (calculatedLinearRegression.isValid(5)) {
			estimation = calculatedLinearRegression.estimate(size);
		} else {
			estimation = heuristicLinearRegression.estimate(size);
		}
		return transform(estimation);
	}

	@Override
	public void addDataPoint(Integer size, Integer un, Integer methods) {
		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		calculatedLinearRegression.addDataPoint(size, methods);
	}

	@Override
	public void estimateCoefficients(Integer size) {
		LinearRegression calculatedLinearRegression = getCalculatedLinearRegression();
		calculatedLinearRegression.estimateCoefficients();
	}
}
