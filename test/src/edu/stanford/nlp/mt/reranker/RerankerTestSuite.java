package mt.reranker;

import junit.framework.Test;
import junit.framework.TestSuite;

public class RerankerTestSuite {
  public static Test suite() {

    TestSuite suite = new TestSuite();

    //Add tests
    suite.addTestSuite(BleuTest.class);
    suite.addTestSuite(EditStatsTest.class);
    suite.addTestSuite(NGramTest.class);
    suite.addTestSuite(SegStatsTest.class);
    
    return suite;
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
