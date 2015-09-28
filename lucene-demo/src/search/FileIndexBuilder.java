/** Build a file-based Lucene inverted index.
 * 
 * @author Scott Sanner
 */

package search;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;

import search.query.PorterStemAnalyzer;

public class FileIndexBuilder {

	public Analyzer  _analyzer; 
	public String    _indexPath;
	
	public FileIndexBuilder(String index_path) {
		
	    // Specify the analyzer for tokenizing text.
	    // The same analyzer should be used for indexing and searching
		_analyzer = new PorterStemAnalyzer(new String[0] /* stopword list */, 
										   false /* do lowercase */, 
										   false /* do stem */);
	
	    // Store the index path
	    _indexPath = index_path;
	}
		
	/** Main procedure for adding files to the index
	 * 
	 * @param files
	 * @param clear_old_index set to true to create a new index, or
	 *                        false to add to a currently existing index
	 * @return
	 */
	public boolean addFiles(List<File> files, boolean clear_old_index) {
	
		try {
		    // The boolean arg in the IndexWriter ctor means to
		    // create a new index, overwriting any existing index
			//
			// NOTES: Set create=false to add to an index (even while
			//        searchers and readers are accessing it... additional
			//        content goes into separate segments).
			//
			//        To merge can use:
			//        IndexWriter.addIndexes(IndexReader[]) and 
			//        IndexWriter.addIndexes(Directory[])
			//
			//        Index is optimized on optimize() or close()
		    IndexWriter w = new IndexWriter(_indexPath, _analyzer, clear_old_index,
		        IndexWriter.MaxFieldLength.UNLIMITED);
		    
		    // Add all files
		    for (File f : files) {
		    	System.out.println("Adding: " + f.getPath());
		    	DocAdder.AddDoc(w, f);
		    }
		    
		    // Close index writer
	        w.optimize();
		    w.close();
		    
		} catch (IOException e) {
			System.err.println(e);
			return false;
		}
		
		return true;
	}
	
	public static void main(String[] args) throws Exception {
		
		String index_path = "src/search/lucene.index";
		FileIndexBuilder b = new FileIndexBuilder(index_path);
		b.addFiles(FileFinder.GetAllFiles("src/search/documents", ".txt", true), 
				true /*clear_old_index = false if adding*/);
		
		IndexDisplay.Display(index_path, System.out);
	}

}
