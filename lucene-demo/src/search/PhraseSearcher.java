/** Example of Lucene search with query snippet retrieval.
 * 
 * @author Scott Sanner
 */

package search;

import java.awt.BorderLayout;
import java.awt.Container;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.*;

import search.query.PorterStemAnalyzer;
import search.query.QuerySpansExtractor;


public class PhraseSearcher {

    public static final String NEWLINE_CHARS   = "\r\n";

	QueryParser _parser;
	IndexReader _reader;
	IndexSearcher _searcher;
	Analyzer _analyzer;
	Analyzer _simpleAnalyzer;
	String _indexPath;

	DecimalFormat _df = new DecimalFormat("#.##");

	public PhraseSearcher(String index_path, String default_field, Analyzer a)
			throws IOException {
		_indexPath = index_path;
		_analyzer = a;
		_parser = new QueryParser(default_field, _analyzer);
		_reader = IndexReader.open(index_path);
		_searcher = new IndexSearcher(index_path);
		_simpleAnalyzer = new PorterStemAnalyzer(new String[0], false, false);
	}

	public void finalize() throws IOException {
		_searcher.close();
	}

	/**
	 * A struct to aggregate query results of an opinion analysis.
	 */
	public class QueryResult {
		public QueryResult(int doc_id, String path, float relevance,
				ArrayList<String> mentions) {
			_nDocID = doc_id;
			_sPath = path;
			_fRelevance = relevance;
			_alMentions = mentions;
		}

		public int _nDocID;
		public String _sPath;
		public float  _fRelevance;
		public ArrayList<String> _alMentions;

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("  - Doc ID:     '" + _nDocID + "'\n");
			sb.append("  - URL:        '" + _sPath + "'\n");
			sb.append("  - Relevance:  " + _df.format(_fRelevance) + "\n");
			for (int i = 0; i < _alMentions.size(); i++) {
				sb.append("  - Mention:    " + Filter(_alMentions.get(i), "", true) + "\n");
			}
			return sb.toString();
		}
	}

	/**
	 * 
	 * @param main_field
	 * @param main_query
	 * @param filter_query
	 * @param context_width
	 * @param num_hits if >0, do a ranked return, otherwise return all docs
	 * @param get_snippets true if snippets needed, false for full content retrieval
	 * @return
	 * @throws Exception
	 */
	public ArrayList<QueryResult> doSearch(String main_field,
			String main_query, String filter_query, int context_width,
			int num_hits, boolean get_snippets)
			throws Exception {

		// Reload index if it has changed
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
		
		Query q = _parser.parse(main_field + ":" + main_query);

		// Retrieve the spans
		Spans[] query_spans = QuerySpansExtractor.getSpans(q, _reader);
		HashMap<Integer, ArrayList<Integer[]>> docIdStartEndPair = new HashMap<Integer, ArrayList<Integer[]>>();
		for (Spans s : query_spans) {
			// System.out.println("Span: " + s);
			while (s.next()) {
				int doc_num = s.doc();
				// Document doc = _reader.document(doc_num);
				// Field f = doc.getField(main_field);

				ArrayList<Integer[]> docStartEndPair = docIdStartEndPair
						.get(doc_num);
				if (docStartEndPair == null) {
					docStartEndPair = new ArrayList<Integer[]>();
				}
				Integer[] startEndPair = new Integer[2];
				startEndPair[0] = s.start();
				startEndPair[1] = s.end();

				docStartEndPair.add(startEndPair);
				docIdStartEndPair.put(doc_num, docStartEndPair);
			}
		}

		// Get top-ranked hits if required
		// TODO: Allow for query boosting in search of multiple fields,
		//       e.g., Title field... will need to search Title in main
		//       query as well, but easy to do with an OR... it seems
		//       only the span for the default field is returned.
		ScoreDoc[] hits = null;
		if (num_hits > 0) {
			// Guarantees that the filter is a subset 
			Query filter = filter_query == null ? q 
				: _parser.parse("(" + main_field + ":" + main_query + ") AND " + filter_query); 
			TopDocCollector collector = new TopDocCollector(num_hits);
			_searcher.search(filter, collector);
			hits = collector.topDocs().scoreDocs;
		}
		
		// Populate the return set of doc IDs
		ArrayList<Integer> docs_return = new ArrayList<Integer>();
		if (num_hits > 0) 
			for (ScoreDoc sd : hits) // Add in order
				docs_return.add(sd.doc);
		else
			docs_return.addAll(docIdStartEndPair.keySet());
		
		// Process the documents for ranking or sorting and extract
		// the snippets in the process... this is a bit inefficient
		// because the first query retrieved more documents, but I'm
		// not sure how to get spans only for the filter restrictions
		ArrayList<QueryResult> results = new ArrayList<QueryResult>();
		for (int i = 0; i < docs_return.size(); i++) {

			// Get snippets for this document
			int doc_id = docs_return.get(i);
			Document doc = _reader.document(doc_id);
			if (docIdStartEndPair.get(doc_id) == null) {
				System.err.println("Document " + doc_id + 
						" in filter query, but not in main query.\n" +
						"Most likely reason is that SpanQuery could " + 
						"not handle query.");
				continue;
			}
			
			// TODO: Allow for arbitrary start and end highlight characters
			ArrayList<String> snippets = get_snippets 
				? getSnippets(doc_id, main_field, context_width, docIdStartEndPair)
				: new ArrayList<String>(Arrays.asList(new String[] { getHighlightedText(doc_id, main_field, context_width, docIdStartEndPair) }));

			// Get the path for this file
			String path = doc.get("PATH");
			
			// Get relevance
			float relevance = 0f;
			if (hits != null)
				relevance = hits[i].score;

			// Build the QueryResult
			results.add(new QueryResult(doc_id, path, relevance, snippets));
		}

		return results;
	}

	/**
	 * 
	 * @param doc_id
	 * @param field
	 * @param context_width
	 * @param docid2start_end_pairs
	 * @return A list of all snippet Strings separated into pre-phrase, 
	 *         phrase, and post-phrase by <b> </b> tags
	 */
	private ArrayList<String> getSnippets(int doc_id, String field,
			int context_width,
			HashMap<Integer, ArrayList<Integer[]>> docid2start_end_pairs)
			throws Exception {

		Document doc = _reader.document(doc_id);
		ArrayList<Integer[]> fromTo = docid2start_end_pairs.get(doc_id);

		// NOTE: query.TokenSources for how to get a TokenStream directly
		// from the TermFreqVector... claimed to be 4x faster
		String field_content = doc.get(field);
		BufferedReader br = new BufferedReader(new StringReader(field_content));
		TokenStream stream = _simpleAnalyzer.tokenStream(field, br);
		int cur_pos = -1;
		boolean inside = false;
		Token t = null;

		// Preinitialize the number of snippets
		StringBuilder[] sb = new StringBuilder[fromTo.size()];
		int[][] offsets = new int[fromTo.size()][];
		boolean[] in_tag = new boolean[fromTo.size()];
		for (int sbi = 0; sbi < fromTo.size(); sbi++) {
			sb[sbi] = new StringBuilder();
			in_tag[sbi] = false;
			// Indices: [0] prefix [1] infix [2] suffix [3]
			offsets[sbi] = new int[] { 0, 0, -1, -1 };
		}

		// Process the tokens one-by-one, adding them to the right
		// snippets
		ArrayList<String> alMentions = new ArrayList<String>();
		int last_offset = 0;
		while ((t = stream.next()) != null) {
			++cur_pos;

			last_offset = t.endOffset();
			for (int sbi = 0; sbi < fromTo.size(); sbi++) {
				// System.out.println("#" + sbi + "  " + cur_pos + " == " +
				// (fromTo.get(sbi)[1] - 1) + ": " + (cur_pos ==
				// fromTo.get(sbi)[1] - 1));
				if (cur_pos == fromTo.get(sbi)[0]) {
					//System.out.println("1 #" + sbi + " =" + cur_pos);
					offsets[sbi][1] = t.startOffset();
					in_tag[sbi] = true;
					sb[sbi].append("<b> " + t.term() + " ");
				}
				if (cur_pos == fromTo.get(sbi)[1] - 1) {
					//System.out.println("2 #" + sbi + " =" + cur_pos);
					offsets[sbi][2] = t.endOffset();
					sb[sbi].append(t.term() + " </b> ");
					in_tag[sbi] = false;
				} 
				if (cur_pos != fromTo.get(sbi)[0] &&
					cur_pos != fromTo.get(sbi)[1] - 1 && 
					cur_pos >= fromTo.get(sbi)[0] - context_width &&
					cur_pos <= fromTo.get(sbi)[1] + context_width) {
					if (cur_pos == fromTo.get(sbi)[0] - context_width)
						offsets[sbi][0] = t.startOffset();
					else if (cur_pos == fromTo.get(sbi)[1] + context_width)
						offsets[sbi][3] = t.endOffset();
					// If weighting needed, currently unused
					int dist = 0;
					if (cur_pos < fromTo.get(sbi)[0])
						dist = fromTo.get(sbi)[0] - cur_pos;
					else if (cur_pos > fromTo.get(sbi)[1])
						dist = cur_pos - fromTo.get(sbi)[1];
					sb[sbi].append(t.term() + " ");
				}
			}
		}

		for (int sbi = 0; sbi < fromTo.size(); sbi++) {
			
			//System.out.println(offsets[sbi][0] + " / " + offsets[sbi][1]
			//		+ " / " + offsets[sbi][2] + " / " + offsets[sbi][3]);

			boolean token_based = false;
			if (token_based)
				alMentions.add(sb[sbi].toString().trim() + (in_tag[sbi] ? "</b>" : ""));		
			else 
				alMentions.add(field_content.substring(offsets[sbi][0],
						offsets[sbi][1] == -1 ? last_offset : offsets[sbi][1])
						+ "<b>"
						+ field_content.substring(
								offsets[sbi][1] == -1 ? last_offset : offsets[sbi][1],
								offsets[sbi][2] == -1 ? last_offset	: offsets[sbi][2])
						+ "</b>"
						+ field_content.substring(
								offsets[sbi][2] == -1 ? last_offset	: offsets[sbi][2],
								offsets[sbi][3] == -1 ? last_offset	: offsets[sbi][3]));
		}

		return alMentions;
	}

	/**
	 * 
	 * @param doc_id
	 * @param field
	 * @param context_width
	 * @param docid2start_end_pairs
	 * @return A list of all snippet Strings separated into pre-phrase, 
	 *         phrase, and post-phrase by <b> </b> tags
	 */
	private String getHighlightedText(int doc_id, String field,	int context_width,
			HashMap<Integer, ArrayList<Integer[]>> docid2start_end_pairs)
			throws Exception {

		Document doc = _reader.document(doc_id);
		ArrayList<Integer[]> fromTo = docid2start_end_pairs.get(doc_id);

		// NOTE: query.TokenSources for how to get a TokenStream directly
		// from the TermFreqVector... claimed to be 4x faster
		String field_content = doc.get(field);
		BufferedReader br = new BufferedReader(new StringReader(field_content));
		TokenStream stream = _simpleAnalyzer.tokenStream(field, br);
		int cur_pos = -1;
		boolean inside = false;
		Token t = null;

		// Preinitialize the number of snippets
		int[][] offsets = new int[fromTo.size()][];
		for (int sbi = 0; sbi < fromTo.size(); sbi++) {
			// Indices: [0] prefix [1] infix [2] suffix [3]
			offsets[sbi] = new int[] { 0, 0, -1, -1 };
		}

		// Process the tokens one-by-one, adding them to the right
		// snippets
		ArrayList<String> alMentions = new ArrayList<String>();
		int last_offset = 0;
		while ((t = stream.next()) != null) {
			++cur_pos;

			last_offset = t.endOffset();
			for (int sbi = 0; sbi < fromTo.size(); sbi++) {
				// System.out.println("#" + sbi + "  " + cur_pos + " == " +
				// (fromTo.get(sbi)[1] - 1) + ": " + (cur_pos ==
				// fromTo.get(sbi)[1] - 1));
				if (cur_pos == fromTo.get(sbi)[0]) {
					//System.out.println("1 #" + sbi + " =" + cur_pos);
					offsets[sbi][1] = t.startOffset();
				}
				if (cur_pos == fromTo.get(sbi)[1] - 1) {
					//System.out.println("2 #" + sbi + " =" + cur_pos);
					offsets[sbi][2] = t.endOffset();
				} 
				if (cur_pos != fromTo.get(sbi)[0] &&
					cur_pos != fromTo.get(sbi)[1] - 1 && 
					cur_pos >= fromTo.get(sbi)[0] - context_width &&
					cur_pos <= fromTo.get(sbi)[1] + context_width) {
					if (cur_pos == fromTo.get(sbi)[0] - context_width)
						offsets[sbi][0] = t.startOffset();
					else if (cur_pos == fromTo.get(sbi)[1] + context_width)
						offsets[sbi][3] = t.endOffset();
					// If weighting needed, currently unused
					int dist = 0;
					if (cur_pos < fromTo.get(sbi)[0])
						dist = fromTo.get(sbi)[0] - cur_pos;
					else if (cur_pos > fromTo.get(sbi)[1])
						dist = cur_pos - fromTo.get(sbi)[1];
				}
			}
		}

		// Go through and remove any overlapping positions to be highlighted
		int max_offset = -1;
		ArrayList<int[]> positions = new ArrayList<int[]>();
		for (int sbi = 0; sbi < fromTo.size(); sbi++) {
		
			if (offsets[sbi][1] > max_offset)
				positions.add(new int[] { offsets[sbi][1], offsets[sbi][2] });
			else 
				positions.get(positions.size() - 1)[1] = offsets[sbi][2];
		
			max_offset = offsets[sbi][2];
		}
			
		// Go through and snip the main string into pieces and append <b>, </b>
		StringBuilder sb = new StringBuilder(field_content.substring(0, 
				positions.size() > 0 ? positions.get(0)[0] : field_content.length())); 
		
		for (int i = 0; i < positions.size(); i++) {
			// Snipped up to previous pos[0]
			int[] pos = positions.get(i);	
			sb.append("<b style=\"color:black;background-color:#ffff66\">" + field_content.substring(pos[0], pos[1]) + "</b>");
			if (i + 1 < positions.size()) {
				int[] pos2 = positions.get(i + 1);
				sb.append(field_content.substring(pos[1], pos2[0]));
			} else
				sb.append(field_content.substring(pos[1], field_content.length()));
		}
		
		return sb.toString();
	}

    public static class RelevanceComparator implements Comparator {

        public int compare(Object qr1, Object qr2){
            double relevance1 = ( (QueryResult) qr1 )._fRelevance;
            double relevance2 = ( (QueryResult) qr2 )._fRelevance;
            if (relevance1 < relevance2) return -1;
            else if(relevance1 == relevance2) return 0;
            return 1;
        }

        public boolean equals(Object o) {
            return (this == o);
        }
    }

    public static String Filter(String str, String to_filter, boolean remove_newline) {
	    StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < str.length(); i++) {
	    	char c = str.charAt(i);
	    	if (NEWLINE_CHARS.indexOf(c) >= 0) {
	    		if (remove_newline)
	    			sb.append(" ");
	    		else
	    			sb.append("<p>"); //"<p>&nbsp;</p><p>"
	    	} else if (c == '\t')
	    		sb.append(" ");
	    	else if (to_filter.indexOf(str.charAt(i)) < 0)
	            sb.append(str.charAt(i));
	    }
	    return sb.toString();
	}

	/**
	 * Helper function to see if HTML is correctly formatted by displaying it in
	 * a lightweight HTML Java Swing window.
	 * 
	 * @param content
	 *            String containing HTML content
	 **/
	public static void LaunchHtmlViewer(String html) {
		JFrame frame = new JFrame("Highlighted Content Display");
		Container content = frame.getContentPane();
	
		JEditorPane editorPane = new JEditorPane();
		editorPane.setEditable(false);
		editorPane.setEditorKit(new HTMLEditorKit());
		editorPane.setText(html);
	
		JScrollPane scrollPane = new JScrollPane(editorPane);
		content.add(scrollPane, BorderLayout.CENTER);
	
		frame.setSize(800, 600);
		frame.setVisible(true);
	}

	public static void main(String[] args) throws Exception {

		String index_path = "src/search/lucene.index";
		String default_field = "CONTENT";

		FileIndexBuilder b = new FileIndexBuilder(index_path);
		PhraseSearcher qs = new PhraseSearcher(b._indexPath, default_field,
				b._analyzer);

		// Simple term query
		//
		// Param 1: Field to retrieve query spans from  
		// Param 2: Query for query spans retrieval
		// Param 3: Filter query (additional restrictions, not for spans)
		// Param 4: Context width
		// Param 5: Number of hits to return and score 
		//          (-1 to return all documents unscored)
		Test(qs, "CONTENT", "Obama", "Obama Hillary", 5, 3, true);

		// Wild card
		Test(qs, "CONTENT", "Ob*ma", null, 5, 3, true);

		// Edit distance
		Test(qs, "CONTENT", "Yobama~.9", null, 5, 3, true);

		// Field
		// Test(qs, "CONTENT", "FIRST_LINE:Obama", "", 5);

		// Boolean
		Test(qs, "CONTENT", "Obama AND Hillary", null, 5, 3, true);

		// Phrase with boost and second filter
		Test(qs, "CONTENT", "\"State secretaries\"~5", null, 5, -1, true);

		// Note: can boost fields including quoted phrases ^,
		// also can indicate slop factor ~ for phrases
		Test(qs, "CONTENT", "\"Barack Obama\"~5",
				"(FIRST_LINE:\"Barack Obama\"~5)", 5, 3, true);

		Test(qs, "CONTENT", "\"State secretaries\"~5", null, 5, 3, false);
		//Test(qs, "CONTENT", "\"secretaries\"~5", null, 5, 3, false);
	}

	public static void Test(PhraseSearcher qs, String field, String query,
			String filter, int context_width, int hits, boolean get_snippets) throws Exception {

		ArrayList<QueryResult> results = qs.doSearch("CONTENT", query, filter,
				context_width, hits, get_snippets);
		System.out.println("=========================================");
		System.out.println("Query: " + field + ":" + query + " s.t. '" + filter
				+ "'\n");
		for (QueryResult qr : results) {
			System.out.println(qr);
			
			// Display html if full content
			if (!get_snippets)			
				LaunchHtmlViewer(Filter(qr._alMentions.get(0), "", false));
		}

		System.out.println("=========================================");

	}

}
