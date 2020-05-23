package pt.ulisboa.tecnico.cnv.server.estimatecomplexity;

public class EstimatorBFS extends EstimatorTwoLevels {
	public EstimatorBFS() {
		super("BFS",
				new LinearRegression(4.33f, -1560f, 100f),
				new LinearRegression(9.41f, 439f, 74.92f));
		setSizeLevelHeuristicRegression(9, new LinearRegression(5.33f, 336f, 72.52f));
		setSizeLevelHeuristicRegression(16, new LinearRegression(11.61f, 372f, 74.06f));
		setSizeLevelHeuristicRegression(25, new LinearRegression(5.64f, 2026f, 37.51f));
	}
}
