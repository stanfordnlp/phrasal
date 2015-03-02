package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.mt.util.PositionIndependentDistance;
import junit.framework.TestCase;

/**
 * @author Ankush Singla
 */
public class PositionIndependentDistanceTest extends TestCase {
    protected PositionIndependentDistance positionIndependentDistance;

    @Override
    protected void setUp(){
        positionIndependentDistance = new PositionIndependentDistance();
    }

    public void testInsert(){
        assertEquals(4.0, positionIndependentDistance.score("Hi!","Hi you!"));    
        assertEquals(5.0, positionIndependentDistance.score("Hi!","Hi you!?"));    
    }

    public void testDelete(){
        assertEquals(4.0, positionIndependentDistance.score("Hi you!","Hi!"));
        assertEquals(5.0, positionIndependentDistance.score("Hi you!?", "Hi!"));
    }

    public void testModify(){
        assertEquals(3.0, positionIndependentDistance.score("Hi you!","Hi Sir!"));
        assertEquals(5.0, positionIndependentDistance.score("Hi you!","Hi Sir!!!"));
    }

    public void testPostionIndependence(){
      assertEquals(0.0, positionIndependentDistance.score("Hi you!","ouy Hi!"));
    }
    
    public void testMultipleCharacters(){
      assertEquals(1.0, positionIndependentDistance.score("Hi you!","ouy Hii!"));
    }
}
