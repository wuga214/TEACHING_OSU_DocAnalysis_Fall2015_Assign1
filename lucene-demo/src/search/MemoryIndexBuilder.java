/** Build a Lucene index in memory.
 * 
 * @author Scott Sanner
 */

package search;

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import search.query.*;


public class MemoryIndexBuilder {

	public Analyzer  _analyzer; 
	public Directory _index;
	
	public MemoryIndexBuilder() {
		
	    // Specify the analyzer for tokenizing text.
	    // The same analyzer should be used for indexing and searching
		_analyzer = new PorterStemAnalyzer(new String[0], true, true);
	
	    // Create the index
	    _index = new RAMDirectory();
	}
		
	public boolean addFiles(List<File> files) {
	
		try {
		    // The boolean arg in the IndexWriter ctor means to
		    // create a new index, overwriting any existing index
		    IndexWriter w = new IndexWriter(_index, _analyzer, true,
		        IndexWriter.MaxFieldLength.UNLIMITED);
		    
		    // Add all files
		    for (File f : files) {
		    	System.out.println("Adding: " + f.getPath());
		    	DocAdder.AddDoc(w, f);
		    }
		    
		    // Close index writer
		    w.close();
		    
		} catch (IOException e) {
			System.err.println(e);
			return false;
		}
		
		return true;
	}
	
	public static void main(String[] args) throws Exception {
		
		MemoryIndexBuilder b = new MemoryIndexBuilder();
		b.addFiles(FileFinder.GetAllFiles("src/search/documents", ".txt", true));
		IndexDisplay.Display(b._index, System.out);
	}
}
