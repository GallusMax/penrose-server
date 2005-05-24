/* Generated By:JavaCC: Do not edit this line. FilterParser.java */
/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose.filter;

import java.util.*;

/**
<pre>

   See: http://www.faqs.org/rfcs/rfc2254.html

        filter     = "(" filtercomp ")"
        filtercomp = and / or / not / item
        and        = "&" filterlist
        or         = "|" filterlist
        not        = "!" filter
        filterlist = 1*filter
        item       = simple / present / substring / extensible
        simple     = attr filtertype value
        filtertype = equal / approx / greater / less
        equal      = "="
        approx     = "~="
        greater    = ">="
        less       = "<="
        extensible = attr [":dn"] [":" matchingrule] ":=" value
                     / [":dn"] ":" matchingrule ":=" value
        present    = attr "=*"
        substring  = attr "=" [initial] any [final]
        initial    = value
        any        = "*" *(value "*")
        final      = value
        attr       = AttributeDescription from Section 4.1.5 of [1]
        matchingrule = MatchingRuleId from Section 4.1.9 of [1]
        value      = AttributeValue from Section 4.1.6 of [1]

   If a value should contain any of the following characters

           Character       ASCII value
           ---------------------------
           *               0x2a
           (               0x28
           )               0x29
           \               0x5c
           NUL             0x00

   the character must be encoded as the backslash '\' character (ASCII
   0x5c) followed by the two hexadecimal digits representing the ASCII
   value of the encoded character. The case of the two hexadecimal
   digits is not significant.
   
   Example usage:
   
   Reader in = ...;
   FilterParser parser = new FilterParser(in);
   try {
     Filter filter = parser.parse();
   } catch (ParseException ex) {
     System.out.println(ex.getMessage());
   }

</pre>
*/

public class FilterParser implements FilterParserConstants {

  Filter parsedFilter;

  public Filter getFilter() { return this.parsedFilter; }

  public Filter parse() throws ParseException {
    parsedFilter = Filter();
    return parsedFilter;
  }

  final public Filter Filter() throws ParseException {
        Filter f = null, filter = null;
        Token mr = null;
    jj_consume_token(LPAREN);
    if (jj_2_1(10)) {
      filter = And();
    } else if (jj_2_2(10)) {
      filter = Or();
    } else if (jj_2_3(10)) {
      filter = Not();
    } else if (jj_2_4(10)) {
      filter = Item();
    } else {
      jj_consume_token(-1);
      throw new ParseException();
    }
    jj_consume_token(RPAREN);
          {if (true) return filter;}
    throw new Error("Missing return statement in function");
  }

  final public Filter And() throws ParseException {
        AndFilter filter;
        Filter f;
    jj_consume_token(AND);
    label_1:
    while (true) {
      if (jj_2_5(10)) {
        ;
      } else {
        break label_1;
      }
      jj_consume_token(SPACE);
    }
      filter = new AndFilter();
    label_2:
    while (true) {
      f = Filter();
          filter.addFilterList(f);
      label_3:
      while (true) {
        if (jj_2_6(10)) {
          ;
        } else {
          break label_3;
        }
        jj_consume_token(SPACE);
      }
      if (jj_2_7(10)) {
        ;
      } else {
        break label_2;
      }
    }
          {if (true) return filter;}
    throw new Error("Missing return statement in function");
  }

  final public Filter Or() throws ParseException {
        OrFilter filter;
        Filter f;
    jj_consume_token(OR);
    label_4:
    while (true) {
      if (jj_2_8(10)) {
        ;
      } else {
        break label_4;
      }
      jj_consume_token(SPACE);
    }
      filter = new OrFilter();
    label_5:
    while (true) {
      f = Filter();
          filter.addFilterList(f);
      label_6:
      while (true) {
        if (jj_2_9(10)) {
          ;
        } else {
          break label_6;
        }
        jj_consume_token(SPACE);
      }
      if (jj_2_10(10)) {
        ;
      } else {
        break label_5;
      }
    }
          {if (true) return filter;}
    throw new Error("Missing return statement in function");
  }

  final public Filter Not() throws ParseException {
        Filter f;
    jj_consume_token(NOT);
    label_7:
    while (true) {
      if (jj_2_11(10)) {
        ;
      } else {
        break label_7;
      }
      jj_consume_token(SPACE);
    }
    f = Filter();
    label_8:
    while (true) {
      if (jj_2_12(10)) {
        ;
      } else {
        break label_8;
      }
      jj_consume_token(SPACE);
    }
          {if (true) return new NotFilter(f);}
    throw new Error("Missing return statement in function");
  }

  final public Filter Item() throws ParseException {
        Filter filter;
        Token item;
    item = jj_consume_token(ITEM);
        String expression = item.toString();
        //System.out.println("Expression: "+expression);

        int p = expression.indexOf("=");
        char c = expression.charAt(p-1);
        int length = 1;

        if (c == '~' || c == '>' || c == '<' || c == ':') {
            p--;
            length = 2;
        }

        String attr = expression.substring(0, p);
        String type = expression.substring(p, p+length);
        String value = expression.substring(p+length);
        //System.out.println("Expression: ["+attr+"] ["+type+"] ["+value+"]");

        if (!"=".equals(type.toString())) {
            filter = new SimpleFilter(attr, type, value);

        } else if ("*".equals(value)) {
            filter = new PresentFilter(attr);

        } else if (value.indexOf('*') < 0) {
            filter = new SimpleFilter(attr, "=", value);

        } else {
            List values = new ArrayList();
            StringTokenizer st = new StringTokenizer(value, "*", true);
            while (st.hasMoreTokens()) {
                values.add(st.nextToken());
            }
            filter = new SubstringFilter(attr, values);
        }
          {if (true) return filter;}
    throw new Error("Missing return statement in function");
  }

  final private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_1(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(0, xla); }
  }

  final private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_2(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(1, xla); }
  }

  final private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_3(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(2, xla); }
  }

  final private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_4(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(3, xla); }
  }

  final private boolean jj_2_5(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_5(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(4, xla); }
  }

  final private boolean jj_2_6(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_6(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(5, xla); }
  }

  final private boolean jj_2_7(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_7(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(6, xla); }
  }

  final private boolean jj_2_8(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_8(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(7, xla); }
  }

  final private boolean jj_2_9(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_9(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(8, xla); }
  }

  final private boolean jj_2_10(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_10(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(9, xla); }
  }

  final private boolean jj_2_11(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_11(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(10, xla); }
  }

  final private boolean jj_2_12(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_12(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(11, xla); }
  }

  final private boolean jj_3_10() {
    if (jj_3R_13()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_9()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  final private boolean jj_3_3() {
    if (jj_3R_11()) return true;
    return false;
  }

  final private boolean jj_3R_12() {
    if (jj_scan_token(ITEM)) return true;
    return false;
  }

  final private boolean jj_3R_9() {
    if (jj_scan_token(AND)) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_5()) { jj_scanpos = xsp; break; }
    }
    if (jj_3_7()) return true;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_7()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  final private boolean jj_3_2() {
    if (jj_3R_10()) return true;
    return false;
  }

  final private boolean jj_3_8() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  final private boolean jj_3R_13() {
    if (jj_scan_token(LPAREN)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_1()) {
    jj_scanpos = xsp;
    if (jj_3_2()) {
    jj_scanpos = xsp;
    if (jj_3_3()) {
    jj_scanpos = xsp;
    if (jj_3_4()) return true;
    }
    }
    }
    if (jj_scan_token(RPAREN)) return true;
    return false;
  }

  final private boolean jj_3R_10() {
    if (jj_scan_token(OR)) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_8()) { jj_scanpos = xsp; break; }
    }
    if (jj_3_10()) return true;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_10()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  final private boolean jj_3_12() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  final private boolean jj_3_11() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  final private boolean jj_3_6() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  final private boolean jj_3R_11() {
    if (jj_scan_token(NOT)) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_11()) { jj_scanpos = xsp; break; }
    }
    if (jj_3R_13()) return true;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_12()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  final private boolean jj_3_4() {
    if (jj_3R_12()) return true;
    return false;
  }

  final private boolean jj_3_7() {
    if (jj_3R_13()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_6()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  final private boolean jj_3_9() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  final private boolean jj_3_1() {
    if (jj_3R_9()) return true;
    return false;
  }

  final private boolean jj_3_5() {
    if (jj_scan_token(SPACE)) return true;
    return false;
  }

  public FilterParserTokenManager token_source;
  SimpleCharStream jj_input_stream;
  public Token token, jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  public boolean lookingAhead = false;
  private boolean jj_semLA;
  private int jj_gen;
  final private int[] jj_la1 = new int[0];
  static private int[] jj_la1_0;
  static {
      jj_la1_0();
   }
   private static void jj_la1_0() {
      jj_la1_0 = new int[] {};
   }
  final private JJCalls[] jj_2_rtns = new JJCalls[12];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  public FilterParser(java.io.InputStream stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new FilterParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.InputStream stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public FilterParser(java.io.Reader stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new FilterParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public FilterParser(FilterParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void ReInit(FilterParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 0; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static private final class LookaheadSuccess extends java.lang.Error { }
  final private LookaheadSuccess jj_ls = new LookaheadSuccess();
  final private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    if (jj_scanpos.kind != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) throw jj_ls;
    return false;
  }

  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = lookingAhead ? jj_scanpos : token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      boolean exists = false;
      for (java.util.Enumeration e = jj_expentries.elements(); e.hasMoreElements();) {
        int[] oldentry = (int[])(e.nextElement());
        if (oldentry.length == jj_expentry.length) {
          exists = true;
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              exists = false;
              break;
            }
          }
          if (exists) break;
        }
      }
      if (!exists) jj_expentries.addElement(jj_expentry);
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[13];
    for (int i = 0; i < 13; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 0; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 13; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

  final private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 12; i++) {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
            case 4: jj_3_5(); break;
            case 5: jj_3_6(); break;
            case 6: jj_3_7(); break;
            case 7: jj_3_8(); break;
            case 8: jj_3_9(); break;
            case 9: jj_3_10(); break;
            case 10: jj_3_11(); break;
            case 11: jj_3_12(); break;
          }
        }
        p = p.next;
      } while (p != null);
    }
    jj_rescan = false;
  }

  final private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
