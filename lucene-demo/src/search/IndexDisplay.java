/** Dump index contents to output stream.
 * 
 * @author Scott Sanner
 */

package search;

import java.io.PrintStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

public class IndexDisplay {

	public static void Display(String index_path, PrintStream ps) 
		throws Exception {
		IndexReader r = IndexReader.open(index_path);
		Display(r, ps);
		r.close();
	}

	public static void Display(Directory index, PrintStream ps) 
		throws Exception {
		// Live Index Updating: 
		//
		// Does Lucene allow searching and indexing simultaneously?
		//
		// Yes. However, an IndexReader only searches the index as of 
		// the "point in time" that it was opened. Any updates to the 
		// index, either added or deleted documents, will not be visible
		// until the IndexReader is re-opened. So your application must 
		// periodically re-open its IndexReaders to see the latest 
		// updates. The [WWW] IndexReader.isCurrent() method allows you 
		// to test whether any updates have occurred to the index since 
		// your IndexReader was opened. 
		IndexReader r = IndexReader.open(index);
		Display(r, ps);
		r.close();
	}

	public static void Display(IndexReader r, PrintStream ps) 
		throws Exception {
		
		for (int d = 0; d < r.maxDoc(); d++) {
			ps.println("=========================================");
			Document doc = r.document(d);
			for (Object o : doc.getFields()) {
				Field f = (Field)o;
				ps.println(f.name() + ": " + f.stringValue());
			}
		}
		ps.println("=========================================");
	}
	
	public static void main(String[] args) throws Exception{
		Display("src/search/lucene.index", System.out);
	}

}
