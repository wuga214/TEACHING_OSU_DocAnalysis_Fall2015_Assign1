package search;

import org.apache.lucene.search.DefaultSimilarity;

public class CustomSimilarity extends DefaultSimilarity {


    /**
	 * looking for more details about similarity function, see web below;
	 * https://lucene.apache.org/core/2_9_4/api/all/org/apache/lucene/search/Similarity.html
	 * http://grepcode.com/file/repo1.maven.org/maven2/org.apache.lucene/lucene-core/2.4.0/org/apache/lucene/search/DefaultSimilarity.java?av=f
	 */
	private static final long serialVersionUID = 1L;

	@Override
    public float queryNorm(float sumOfSquaredWeights) {
		return (float)(1.0 / Math.sqrt(sumOfSquaredWeights));
    }

    @Override
    public float tf(float freq) {
    	return (float)Math.sqrt(freq);
    }

    @Override
    public float idf(int docFreq, int numDocs) {
    	return (float)(Math.log(numDocs/(double)(docFreq+1)) + 1.0);
    }
    
    @Override
    public float coord(int overlap, int maxOverlap) {
    	return overlap / (float)maxOverlap;
    }
    
    @Override
    public float lengthNorm(String fieldName, int numTerms) {
    	return (float)(1.0 / Math.sqrt(numTerms));
    }

    @Override
    public float sloppyFreq(int distance) {
    	return 1.0f / (distance + 1);
    	}
}