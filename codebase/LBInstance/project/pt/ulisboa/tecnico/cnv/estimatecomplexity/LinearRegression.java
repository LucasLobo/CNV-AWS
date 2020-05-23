package pt.ulisboa.tecnico.cnv.estimatecomplexity;

public class LinearRegression {

	// Final values
	private Float intercept;
	private Float slope;
	private Float r2;

	// Calculation values
	private Integer n = 0;
	private Integer sum_x = 0;
	private Integer sum_y = 0;
	private Integer sum_xy = 0;
	private Integer sum_xx = 0;
	private Float SS_xx = 0f;

	public LinearRegression() {}

	public LinearRegression(Float slope, Float intercept, Float r2) {
		this.slope = slope;
		this.intercept = intercept;
		this.r2 = r2;
	}

	public void addDataPoint(Integer x, Integer y) {
		n += 1;
		sum_x += x;
		sum_y += y;
		sum_xy += x*y;
		sum_xx += x*x;
	}

	void estimateCoefficients() {
		float mean_x = ((float) sum_x / n);
		float mean_y = ((float) sum_y / n);
		float SS_xy = sum_xy - n * mean_x * mean_y;
		SS_xx = sum_xx - n * mean_x * mean_x;

		slope = SS_xy / SS_xx;
		intercept = mean_y - slope * mean_x;
	}

	public Boolean isValid(Integer minSamples) {
		return minSamples >= 5 && SS_xx != 0;
	}

	public Integer estimate(Integer value) {
		return Math.round(value * slope + intercept);
	}
}
