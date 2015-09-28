/** Simple helper class to read a file and construct fields for indexing
 *  with an IndexWriter (can be memory of file-based).  Called by
 *  MemoryIndexBuilder and FileIndexBuilder.
 * 
 * @author Scott Sanner
 */

package search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

public class DocAdder {
	
	public static void AddDoc(IndexWriter w, File f) {
		try {
		    Document doc = new Document();
		    BufferedReader br = new BufferedReader(new FileReader(f));
		    StringBuilder content = new StringBuilder();
		    String line = null;
		    String first_line = null;
		    while ((line = br.readLine()) != null) {
		    	if (first_line == null)
		    		first_line = line;
		    	content.append(line + "\n");
		    }
		    doc.add(new Field("PATH", f.getPath(), 
		    		Field.Store.YES, Field.Index.NO));
		    
		    doc.add(new Field("FIRST_LINE", first_line, 
		    		Field.Store.YES, Field.Index.ANALYZED,
					Field.TermVector.WITH_POSITIONS_OFFSETS));
		    
		    doc.add(new Field("CONTENT", content.toString(), 
		    		Field.Store.YES, Field.Index.ANALYZED,
					Field.TermVector.WITH_POSITIONS_OFFSETS));
		    
		    w.addDocument(doc);
		} catch (IOException e) {
			System.err.println("Could not add file '" + f + "': " + e);
			e.printStackTrace(System.err);
		}

	}

}
