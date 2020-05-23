package pt.ulisboa.tecnico.cnv.server.estimatecomplexity;

public abstract class Estimator {

	private final String solver;
	private final LinearRegression timeTransformRegression;
	private final LinearRegression heuristicLinearRegression;
	private final LinearRegression calculatedLinearRegression;

	public Estimator(String solver, LinearRegression timeTransformRegression, LinearRegression heuristicLinearRegression) {
		this.solver = solver;
		this.timeTransformRegression = timeTransformRegression;
		this.heuristicLinearRegression = heuristicLinearRegression;
		this.calculatedLinearRegression = new LinearRegression();
	}

	public String getSolver() {
		return solver;
	}

	public LinearRegression getHeuristicLinearRegression() {
		return heuristicLinearRegression;
	}

	public LinearRegression getCalculatedLinearRegression() {
		return calculatedLinearRegression;
	}

	public abstract Integer estimate(Integer size, Integer un);

	public Integer transform(Integer estimation) {
		return timeTransformRegression.estimate(estimation);
	}

	public abstract void addDataPoint(Integer size, Integer un, Integer methods);

	public abstract void estimateCoefficients(Integer size);
}
