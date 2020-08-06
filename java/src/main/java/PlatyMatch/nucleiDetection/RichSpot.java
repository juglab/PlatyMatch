package PlatyMatch.nucleiDetection;

public class RichSpot {
  private String label;
  private double x;
  private double y;
  private double z;


  public RichSpot(String label, double x, double y, double z) {
    this.label = label;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public RichSpot(String label, double x, double y, double z, String geneName, double geneIntensity, boolean geneOn) {
    this.label = label;
    this.x = x;
    this.y = y;
    this.z = z;

  }


  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public double getZ() {
    return z;
  }

  public String getLabel() {
    return label;
  }


}
