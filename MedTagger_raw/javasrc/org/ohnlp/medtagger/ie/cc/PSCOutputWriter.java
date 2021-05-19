package org.ohnlp.medtagger.ie.cc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JFSIndexRepository;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.typesystem.type.structured.Document;
import org.ohnlp.typesystem.type.syntax.NewlineToken;
import org.ohnlp.typesystem.type.textspan.Sentence;


/**
 * 
 * Generic CAS Consumer 
 * @author Sunghwan Sohn
 *
 */
public class PSCOutputWriter extends CasConsumer_ImplBase {
	public static final String PARAM_OUTPUT_FILE = "OutputFile";
	private BufferedWriter iv_bw = null;

	public void initialize() throws ResourceInitializationException {
		File outFile;
		
		try
		{
			String filename = (String) getConfigParameterValue(PARAM_OUTPUT_FILE);
			outFile = new File(filename);
			if (!outFile.exists())
				outFile.createNewFile();
			iv_bw = new BufferedWriter(new FileWriter(outFile));
		} catch (Exception ioe)
		{
			throw new ResourceInitializationException(ioe);
		}
	}

	/**
	 * process
	 */
	public void processCas(CAS aCAS) throws ResourceProcessException {
		JCas jcas;
		try {
			jcas = aCAS.getJCas();
			JFSIndexRepository indexes = jcas.getJFSIndexRepository();
			FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);

			if (docIterator.hasNext()) {
				Document docAnn = (Document) docIterator.next();
				String[] parts = docAnn.getFileLoc().split("/");
				String docName = parts[parts.length-1]; 

				//print out results
				FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(
						ConceptMention.type).iterator();	    
				while (cmIter.hasNext()) {
					ConceptMention cm = (ConceptMention) cmIter.next();	
					
					String output = docName+"|"
							+cm.getSentence().getSegment().getId()+"|"
							+cm.getCoveredText()+"|"
							+cm.getBegin()+"|"
							+cm.getEnd()+"|"
							+cm.getNormTarget()+"|"							
							+cm.getCertainty()+"|"
							+cm.getStatus()+"|"
							+cm.getExperiencer()+"|"
							+cm.getSentence().getCoveredText();

					iv_bw.write(output+"\n");
				}
			}
		} catch (Exception e) {
			throw new ResourceProcessException(e);
		}
	}
	
	public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException
	{
		super.collectionProcessComplete(arg0);

		try
		{
			iv_bw.flush();
			iv_bw.close();
		}
		catch(Exception e)
		{ throw new ResourceProcessException(e); }
	}
}
