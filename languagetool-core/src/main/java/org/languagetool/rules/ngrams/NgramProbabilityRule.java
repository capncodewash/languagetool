/* LanguageTool, a natural language style checker 
 * Copyright (C) 2015 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules.ngrams;

import org.languagetool.AnalyzedSentence;
import org.languagetool.Experimental;
import org.languagetool.Language;
import org.languagetool.languagemodel.LanguageModel;
import org.languagetool.rules.Category;
import org.languagetool.rules.ITSIssueType;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.tokenizers.Tokenizer;
import org.languagetool.tools.StringTools;

import java.util.*;

/**
 * LanguageTool's probability check that uses ngram lookups
 * to decide if an ngram of the input text is so rare in our
 * ngram index that it should be considered an error.
 * Also see <a href="http://wiki.languagetool.org/finding-errors-using-n-gram-data">http://wiki.languagetool.org/finding-errors-using-n-gram-data</a>.
 * @since 3.2
 */
@Experimental
public class NgramProbabilityRule extends Rule {

  /** @since 3.2 */
  public static final String RULE_ID = "NGRAM_RULE";
  
  private static final boolean DEBUG = false;

  private final LanguageModel lm;
  private final Language language;

  private double minProbability = 0.000000000000001;

  public NgramProbabilityRule(ResourceBundle messages, LanguageModel languageModel, Language language) {
    super(messages);
    setCategory(new Category(messages.getString("category_typo")));
    setLocQualityIssueType(ITSIssueType.NonConformance);
    this.lm = Objects.requireNonNull(languageModel);
    this.language = Objects.requireNonNull(language);
  }

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Experimental
  public void setMinProbability(double minProbability) {
    this.minProbability = minProbability;
  }

  /*
    Without bigrams:
      1.0E-6                : f=0.390, precision=0.260, recall=0.784
      1.0E-7                : f=0.391, precision=0.261, recall=0.784
      1.0E-8                : f=0.400, precision=0.267, recall=0.794
      1.0E-9                : f=0.422, precision=0.286, recall=0.804
      1.0000000000000002E-10: f=0.420, precision=0.290, recall=0.765
      1.0000000000000003E-11: f=0.491, precision=0.350, recall=0.824
      1.0000000000000004E-12: f=0.505, precision=0.377, recall=0.765
      1.0000000000000004E-13: f=0.554, precision=0.438, recall=0.755
      1.0000000000000005E-14: f=0.594, precision=0.503, recall=0.725
      1.0000000000000005E-15: f=0.645, precision=0.602, recall=0.696 *
      1.0000000000000005E-16: f=0.589, precision=0.611, recall=0.569
      1.0000000000000005E-17: f=0.536, precision=0.623, recall=0.471

     With bigram occurrences added:
      1.0E-22               : f=0.418, precision=0.285, recall=0.784
      1.0000000000000001E-23: f=0.446, precision=0.307, recall=0.814
      1.0000000000000001E-24: f=0.449, precision=0.316, recall=0.775
      1.0000000000000002E-25: f=0.485, precision=0.353, recall=0.775
      1.0000000000000002E-26: f=0.511, precision=0.382, recall=0.775
      1.0000000000000002E-27: f=0.536, precision=0.409, recall=0.775
      1.0000000000000003E-28: f=0.539, precision=0.422, recall=0.745
      1.0000000000000004E-29: f=0.551, precision=0.448, recall=0.716
      1.0000000000000004E-30: f=0.591, precision=0.503, recall=0.716
      1.0000000000000005E-31: f=0.602, precision=0.548, recall=0.667 *
      1.0000000000000006E-32: f=0.590, precision=0.574, recall=0.608
      1.0000000000000007E-33: f=0.566, precision=0.583, recall=0.549
   */
  @Override
  public RuleMatch[] match(AnalyzedSentence sentence) {
    String text = sentence.getText();
    List<GoogleToken> tokens = GoogleToken.getGoogleTokens(text, true, getGoogleStyleWordTokenizer());
    List<RuleMatch> matches = new ArrayList<>();
    GoogleToken prevPrevToken = null;
    GoogleToken prevToken = null;
    int i = 0;
    for (GoogleToken googleToken : tokens) {
      String token = googleToken.token;
      if (prevPrevToken != null && prevToken != null) {
        if (i < tokens.size()-1) {
          GoogleToken next = tokens.get(i+1);
          Probability p = lm.getPseudoProbability(Arrays.asList(prevToken.token, token, next.token));
          //System.out.println("P=" + p + " for " + Arrays.asList(prevToken.token, token, next.token));
          String ngram = prevToken + " " + token + " " + next.token;
          // without bigrams:
          double prob = p.getProb();
          // with bigrams:
          //Probability bigramLeftP = getPseudoProbability(Arrays.asList(prevToken.token, token));
          //Probability bigramRightP = getPseudoProbability(Arrays.asList(token, next.token));
          //double prob = p.getProb() + bigramLeftP.getProb() + bigramRightP.getProb();
          //System.out.println(prob + " for " + prevToken.token +" "+ token +" "+ next.token);
          if (prob < minProbability) {
            String message = "ngram '" + ngram + "' rarely occurs in ngram reference corpus";
            RuleMatch match = new RuleMatch(this, prevToken.startPos, next.endPos, message);
            matches.add(match);
          }
        }
      }
      prevPrevToken = prevToken;
      prevToken = googleToken;
      i++;
    }
    return matches.toArray(new RuleMatch[matches.size()]);
  }
  
  @Override
  public String getDescription() {
    //return Tools.i18n(messages, "statistics_rule_description");
    return "Assume errors for ngrams that occur rarely in the reference index";
  }

  @Override
  public void reset() {
  }

  protected Tokenizer getGoogleStyleWordTokenizer() {
    return language.getWordTokenizer();
  }
  
  private void debug(String message, Object... vars) {
    if (DEBUG) {
      System.out.printf(Locale.ENGLISH, message, vars);
    }
  }
  
}
