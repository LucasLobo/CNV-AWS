package pt.ulisboa.tecnico.cnv.estimatecomplexity;

public class EstimatorCP extends EstimatorTwoLevels {
	public EstimatorCP() {
		super("CP",
				new LinearRegression(6.71f, 0f, 100f),
				new LinearRegression(13.59f, 12f, 81.22f));
		setSizeLevelHeuristicRegression(9, new LinearRegression(2.85f, 386f, 79.09f));
		setSizeLevelHeuristicRegression(16, new LinearRegression(6.32f, 868f, 49.09f));
		setSizeLevelHeuristicRegression(25, new LinearRegression(11.07f, 1726f, 61.71f));
	}
}
