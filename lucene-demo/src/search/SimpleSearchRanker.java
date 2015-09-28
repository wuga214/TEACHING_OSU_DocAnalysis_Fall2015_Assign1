/** Exhibits standard Lucene searches for ranking documents.
 * 
 * @author Scott Sanner
 */

package search;

import java.io.*;
import java.text.DecimalFormat;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocCollector;
import search.CustomSimilarity;

public class SimpleSearchRanker {

	String        _indexPath;
	Analyzer      _analyzer; 
	QueryParser   _parser;
	IndexReader   _reader;
	IndexSearcher _searcher;
	DecimalFormat _df = new DecimalFormat("#.####");
	
	public SimpleSearchRanker(String index_path, String default_field, Analyzer a) 
		throws IOException {
		_indexPath = index_path;
		_parser    = new QueryParser(default_field, a);
		_reader    = IndexReader.open(index_path);
		_searcher  = new IndexSearcher(index_path);
		_analyzer  = a;
	}
	
	public void finalize() 
		throws IOException {
		_searcher.close();
	}
	
	public void refreshIndex() 
		throws Exception  {

		if (!_reader.isCurrent()) {
			
			// Close the current reader and searcher
			_reader.close();
			_searcher.close();
			_reader = null;
			_searcher = null;
			
			// Optimize the index
		    IndexWriter w = new IndexWriter(_indexPath, _analyzer, false,
			        IndexWriter.MaxFieldLength.UNLIMITED);
	        w.optimize();
		    w.close();

			// Load a new reader and searcher
			_reader = IndexReader.open(_indexPath);
			_searcher = new IndexSearcher(_indexPath);
		}

	}
	
	public void doSearch(String query, int num_hits, PrintStream ps) 
		throws Exception {
		
		Query q = _parser.parse(query);
		TopDocCollector collector = new TopDocCollector(num_hits);
		
		//To use customized similarity function, uncomment the following codes
		//
		//CustomSimilarity similarity =new CustomSimilarity();
		//_searcher.setSimilarity(similarity);
		
		
		_searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		ps.println("Found " + hits.length + " hits.");
		for (int i = 0; i < hits.length; i++) {
		    int docId = hits[i].doc;
		    Document d = _searcher.doc(docId);
		    ps.println((i + 1) + ". (" + _df.format(hits[i].score) 
		    		   + ") " + d.get("PATH"));
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		String index_path = "src/search/lucene.index";
		String default_field = "CONTENT";
		
		FileIndexBuilder b = new FileIndexBuilder(index_path);
		SimpleSearchRanker r = new SimpleSearchRanker(b._indexPath, default_field, b._analyzer);
		
		// See the following for query parser syntax
		//   https://lucene.apache.org/core/2_9_4/queryparsersyntax.html(web for 2_4_1 doesn't work anymore)
		//
		// See the following site for changing weights of the following:
		//   http://www.lucenetutorial.com/advanced-topics/scoring.html
		//   https://lucene.apache.org/core/2_9_4/api/all/org/apache/lucene/search/Similarity.html(web for 2_4_1 doesn't work anymore)
		//   1. tf = term frequency in document = measure of how often a term appears in the document
		//   2. idf = inverse document frequency = measure of how often the term appears across the index
		//   3. coord = number of terms in the query that were found in the document
		//   4. lengthNorm = measure of the importance of a term according to the total number of terms in the field
		//   5. queryNorm = normalization factor so that queries can be compared
		//   6. boost (index) = boost of the field at index-time
		//   7. boost (query) = boost of the field at query-time
		//
		// IN SHORT: the default scoring function for OR terms is a variant of TF-IDF
		//           where one can individually boost the importance of query terms with
		//           a multipler using ^
		//
		// BM25 queries are not shown here, but if desired, the following
		// describes how: 
		//   http://ipl.cs.aueb.gr/stougiannis/bm25.html
		// (note that this requires an indexing modification)
		
		// Standard single term
		r.doSearch("Obama", 5, System.out);

		// Multiple term (implicit OR)
		r.doSearch("Obama Hillary", 5, System.out);

		// Wild card
		r.doSearch("Ob*ma", 5, System.out);
		
		// Edit distance
		r.doSearch("Obama~.4", 5, System.out);
		
		// Fielded search (FIELD:...), boolean (AND OR NOT)
		r.doSearch("FIRST_LINE:Obama AND Hillary", 5, System.out);
		r.doSearch("FIRST_LINE:Obama AND NOT Hillary", 5, System.out);

		// Phrase search (slop factor ~k allows words to be within k distance)
		r.doSearch("\"Barack Obama\"", 5, System.out);
		r.doSearch("\"Barack Obama\"~5", 5, System.out);
		
		// Note: can boost terms or subqueries using ^ (e.g., ^10 or ^.1) -- default is 1 
		r.doSearch("Obama^10 Hillary^0.1", 5, System.out);
		r.doSearch("(FIRST_LINE:\"Barack Obama\")^10 OR Hillary^0.1", 5, System.out);

		// Reversing boost... see change in ranking 
		r.doSearch("Obama^0.1 Hillary^10", 5, System.out);
		r.doSearch("(FIRST_LINE:\"Barack Obama\")^0.1 OR Hillary^10", 5, System.out);

		// Complex query
		r.doSearch("(FIRST_LINE:\"Barack Obama\"~5^10 AND Obama~.4) OR Hillary", 5, System.out);
	}

}
