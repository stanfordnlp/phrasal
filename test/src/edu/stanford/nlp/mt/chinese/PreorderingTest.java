package edu.stanford.nlp.mt.chinese;

import junit.framework.TestCase;
/**
 * @author Rob Voigt
 */
public class PreorderingTest extends TestCase {

  ChineseSourcePreordering preorder;

  public void setUp() {
    preorder = new ChineseSourcePreordering();
  }

  public void testPatterns() {
    assertEquals("Wrong preordering for VP(PP:VP)",
            "(TOP (IP (VP (VP (VV 名列) (QP (OD 第十))) (PP (P 在) (NP (NN 东部) (NN 联盟))))))",
            ChineseSourcePreordering.preorder("(TOP (IP (VP (PP (P 在) (NP (NN 东部) (NN 联盟))) (VP (VV 名列) (QP (OD 第十))))))"));
    
    assertEquals("Wrong preordering for VP(LCP:VP)",
            "(TOP (IP (VP (VP (VV 发表) (NP (NN 声明))) (NP (NT 当天) (NT 上午)))))",
            ChineseSourcePreordering.preorder("(TOP (IP (VP (NP (NT 当天) (NT 上午)) (VP (VV 发表) (NP (NN 声明))))))"));
    
    assertEquals("Wrong preordering for VP(NT:VP)",
            "(TOP (IP (VP (VP (VV 受伤)) (QP (CD 多) (CLP (M 次))))))",
            ChineseSourcePreordering.preorder("(TOP (IP (VP (QP (CD 多) (CLP (M 次))) (VP (VV 受伤)))))"));
    
    assertEquals("Wrong preordering for VP(QP:VP)",
            "(TOP (NP (NP (NN 经济) (NN 援助)) (DNP (PP (P 对) (NP (NR 津巴布韦))) (DEG 的))))",
            ChineseSourcePreordering.preorder("(TOP (NP (DNP (PP (P 对) (NP (NR 津巴布韦))) (DEG 的)) (NP (NN 经济) (NN 援助))))"));
    
    assertEquals("Wrong preordering for NP(CP:NP)",
            "(TOP (NP (NP (NN 掌握)) (DNP (NP (DP (DT 该) (CLP (M 项))) (NP (NN 技术))) (DEG 的))))",
            ChineseSourcePreordering.preorder("(TOP (NP (DNP (NP (DP (DT 该) (CLP (M 项))) (NP (NN 技术))) (DEG 的)) (NP (NN 掌握))))"));
    
  }
}
