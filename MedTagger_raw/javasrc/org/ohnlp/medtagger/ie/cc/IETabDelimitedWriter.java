/*******************************************************************************
 * Copyright: (c)  2013  Mayo Foundation for Medical Education and 
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *  
 *  Except as contained in the copyright notice above, or as used to identify 
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *   
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *   
 *  http://www.apache.org/licenses/LICENSE-2.0 
 *   
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and 
 *  limitations under the License. 
 *******************************************************************************/
package org.ohnlp.medtagger.ie.cc;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.ohnlp.medtagger.ie.cc.AsthmaAPIPtLevelOutputWriter.Info;
import org.ohnlp.medtagger.ie.type.Match;
import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.typesystem.type.structured.Document;
import org.ohnlp.typesystem.type.textspan.Sentence;


/**
 * 
 * Generate XML output format
 * @author Hongfang Liu
 *
 */
public class IETabDelimitedWriter extends CasConsumer_ImplBase {

	public static final String PARAM_OUTPUTDIR = "OutputDir";

	private File mOutputDir;
 
	private int mDocNum;
	
	/**
	 * initialize
	 */
	public void initialize() throws ResourceInitializationException {
    
		mDocNum = 0;
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		if (!mOutputDir.exists()) {
			mOutputDir.mkdirs();
		} 
	}


	/**
	 * process
	 */
	public void processCas(CAS aCAS) throws ResourceProcessException {

		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}
		printAnnotationsInline(jcas);
	}
	
	
		
		// print out match and concept mention
		
	public void printAnnotationsInline(JCas jcas){
		JFSIndexRepository indexes = jcas.getJFSIndexRepository();
	 	FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);
	 	 File outFile = null;
		    File inFile = null;
		  if (docIterator.hasNext()) {
	    	Document docAnn = (Document) docIterator.next();
	 	    String fileLoc =docAnn.getFileLoc(); 
	      try {
	        inFile = new File(new URL(fileLoc).getPath());
	        String outFileName = inFile.getName();
//	        outFile = new File(mOutputDir, outFileName+".ann");
	        outFile = new File(mOutputDir, outFileName);
	      } catch (MalformedURLException e1) {
	       }
	    }
	    if (outFile == null) {
	      outFile = new File(mOutputDir, "doc" + mDocNum++);
	    }
	    
	    String toprint = "";
	    
	   	// get concept mention index
	    FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(ConceptMention.type).iterator();
	    FSIterator<? extends Annotation> senIter = jcas.getAnnotationIndex(Sentence.type).iterator();
		Sentence metaSen = (Sentence) senIter.next();
		String metaSenstr="";
		if(metaSen.getCoveredText().startsWith("[meta")){ metaSenstr=metaSen.getCoveredText();} 
		while (cmIter.hasNext()) {
			ConceptMention cm = (ConceptMention) cmIter.next();
				toprint += inFile.getName()+"\t"+metaSenstr+"\t";
				toprint += "CM\ttext=\""+cm.getCoveredText()+"\""; // covered text;
				toprint += "\tstart=\"" + cm.getBegin() + "\"\t" + "end=\"" + cm.getEnd() + "\""; 
				toprint += "\tcertainty=\""+ cm.getCertainty() + "\""; 
				toprint += "\tstatus=\""+cm.getStatus()+"\"";
				toprint	+="\texperiencer=\""+cm.getExperiencer()+"\"";
				toprint += "\tnorm=\"" + cm.getNormTarget() + "\"";
				toprint += "\tsemG=\"" + cm.getSemGroup()+ "\"";
				toprint += "\tsection=\"" + cm.getSentence().getSegment().getId() + "\"";
				toprint += "\tsentid=\"" + cm.getSentence().getId()+"\"";
				toprint += "\tsentence=\"" +cm.getSentence().getCoveredText() +"\"\n";
		}
		// get concept mention index
	    FSIterator<? extends Annotation> mIter = jcas.getAnnotationIndex(Match.type).iterator();
		while (mIter.hasNext()) {
			Match m = (Match) mIter.next();
			toprint += inFile.getName()+"\t"+metaSenstr+"\t";		
			toprint += "M\ttext=\""+m.getCoveredText()+"\""; // covered text;
				toprint += "\tstart=\"" + m.getBegin() + "\"\t" + "end=\"" + m.getEnd() + "\""; 
				toprint += "\tnorm=\"" + m.getValue()+ "\"";
				toprint += "\trule=\"" + m.getFoundByRule() + "\"";
				toprint += "\tsection=\"" + m.getSentence().getSegment().getId() + "\"";
				toprint += "\tsentid=\"" + m.getSentence().getId()+"\"";
				toprint += "\tsentence=\"" +m.getSentence().getCoveredText() +"\"\n";
		}
		
		try {
			BufferedWriter bf = new BufferedWriter(new FileWriter(outFile));
			bf.append(toprint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}		
	}

	public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException {
		super.collectionProcessComplete(arg0);
		
		
	//end collection
	}
	
//end class
}
