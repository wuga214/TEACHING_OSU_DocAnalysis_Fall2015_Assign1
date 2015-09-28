/** @author: Otis Gospodnetic, Scott Sanner (ssanner@gmail.com) **/

/**          This class based on freely available code found at
 **          http://www.onjava.com/pub/a/onjava/2003/01/15/lucene.html?page=2
 **/

package search.query;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LetterTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.PorterStemFilter;

import java.io.Reader;
import java.util.Hashtable;
import java.util.Set;

/**
 * PorterStemAnalyzer processes input
 * text by stemming English words to their roots.
 * This Analyzer also converts the input to lower case
 * and removes stop words.  A small set of default stop
 * words is defined in the STOP_WORDS
 * array, but a caller can specify an alternative set
 * of stop words by calling non-default constructor.
 */
public class PorterStemAnalyzer extends Analyzer
{
    private static Set _stopTable;
    private boolean _bDoLowerCase;
    private boolean _bDoStem;

    /**
     * An array containing some common English words
     * that are usually not useful for searching.
     */
    public static final String[] STOP_WORDS =
    {
        "0", "1", "2", "3", "4", "5", "6", "7", "8",
        "9", "000", "$",
        "about", "after", "all", "also", "an", "and",
        "another", "any", "are", "as", "at", "be",
        "because", "been", "before", "being", "between",
        "both", "but", "by", "came", "can", "come",
        "could", "did", "do", "does", "each", "else",
        "for", "from", "get", "got", "has", "had",
        "he", "have", "her", "here", "him", "himself",
        "his", "how","if", "in", "into", "is", "it",
        "its", "just", "like", "make", "many", "me",
        "might", "more", "most", "much", "must", "my",
        "never", "now", "of", "on", "only", "or",
        "other", "our", "out", "over", "re", "said",
        "same", "see", "should", "since", "so", "some",
        "still", "such", "take", "than", "that", "the",
        "their", "them", "then", "there", "these",
        "they", "this", "those", "through", "to", "too",
        "under", "up", "use", "very", "want", "was",
        "way", "we", "well", "were", "what", "when",
        "where", "which", "while", "who", "will",
        "with", "would", "you", "your",
        "a", "b", "c", "d", "e", "f", "g", "h", "i",
        "j", "k", "l", "m", "n", "o", "p", "q", "r",
        "s", "t", "u", "v", "w", "x", "y", "z"
    };

    /**
     * Builds an analyzer.
     */
    public PorterStemAnalyzer()
    {
        this(STOP_WORDS, true, true);
    }

    public PorterStemAnalyzer(String[] stopWords)
    {
    	this(stopWords, true, true);
    }
    
    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopWords a String array of stop words
     */
    public PorterStemAnalyzer(String[] stopWords, boolean do_lower_case, boolean do_stem)
    {
        _stopTable = StopFilter.makeStopSet(stopWords);
        _bDoLowerCase = do_lower_case;
        _bDoStem = do_stem;
    }

    /**
     * Processes the input by first converting it to
     * lower case, then by eliminating stop words, and
     * finally by performing Porter stemming on it.
     *
     * @param reader the Reader that
     *               provides access to the input text
     * @return an instance of TokenStream
     */
    public final TokenStream tokenStream(Reader reader)
    {
    	return tokenStream(null, reader);
    }

	@Override
	// We can ignore field
	public TokenStream tokenStream(String field, Reader arg1) {
		if (_bDoLowerCase) {
			TokenFilter base = new StopFilter(new LowerCaseTokenizer(arg1),
                    _stopTable);
			if (_bDoStem)
				return new PorterStemFilter(base);
			else 
				return base;
		} else {
			TokenFilter base = new StopFilter(new LetterTokenizer(arg1),
                    _stopTable);
			if (_bDoStem)
				return new PorterStemFilter(base);
			else 
				return base;
		}
			
	}
}