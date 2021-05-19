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

import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.typesystem.type.structured.Document;
import org.ohnlp.typesystem.type.syntax.NewlineToken;
import org.ohnlp.typesystem.type.textspan.Sentence;


/**
 * 
 * Generate Dr. Olson's PAD output 
 * @author Sunghwan Sohn
 *
 */
public class PADOutputWriter extends CasConsumer_ImplBase {
	class Info {
		String text; //text window
		ConceptMention diag;
		ConceptMention location;
		ConceptMention uncertain;
		
		public Info() {}
		
		public Info(String tx, ConceptMention dia, ConceptMention loc, ConceptMention unc) {
			text = tx;
			diag = dia;
			location = loc;
			uncertain = unc;
		}
	}
	
	public static final String PARAM_OUTPUTDIR = "OutputDir";

	private File mOutputDir;
 
	private int mDocNum;
	
	private Boolean isDocLevelUncert = true; //TODO make it default if ok
	
	//original = 2
	private int senDist = 2; //sentence distance between location and diagnosis

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
		
		//printAnnotationsInline(jcas);
		
//		JFSIndexRepository indexes = jcas.getJFSIndexRepository();
//		FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);
//		String docName = "";
//		if (docIterator.hasNext()) {
//			Document docAnn = (Document) docIterator.next();
//			String fileLoc =docAnn.getFileLoc(); 
//			String[] f = fileLoc.split("/");
//			docName = f[f.length-1];
//		}
//		  
//        System.out.println("---"+docName+" processing---");
		
		List<ConceptMention> location1and2and2_1 = new ArrayList<ConceptMention>();
		List<ConceptMention> location1and2 = new ArrayList<ConceptMention>();
		List<ConceptMention> location2 = new ArrayList<ConceptMention>();
		List<ConceptMention> location2_1 = new ArrayList<ConceptMention>();
		List<ConceptMention> firstDiag = new ArrayList<ConceptMention>();
		List<ConceptMention> firstDiag2 = new ArrayList<ConceptMention>();
		List<ConceptMention> firstDiagOcclusion = new ArrayList<ConceptMention>();
		List<ConceptMention> secondDiag = new ArrayList<ConceptMention>();
		List<ConceptMention> thirdDiag = new ArrayList<ConceptMention>();
		//List<ConceptMention> firstExcl = new ArrayList<ConceptMention>();
		//List<ConceptMention> secondExcl = new ArrayList<ConceptMention>();
		//List<ConceptMention> amputationExcl = new ArrayList<ConceptMention>();
		List<ConceptMention> uncertain = new ArrayList<ConceptMention>();
		
		FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(
				ConceptMention.type).iterator();
		while (cmIter.hasNext()) {
			ConceptMention cm = (ConceptMention) cmIter.next();
			
			//skip section
			//20135: Anticipated Problems and Interventions, 20109: Family History
			String sec = cm.getSentence().getSegment().getId();
			if(sec.equals("20135") || sec.equals("20109")) continue;
				
			//skip negative cases
			if(!cm.getDetectionMethod().equals("Matched")) continue;
			if(!cm.getCertainty().equals("Positive")) continue;
			if(!cm.getExperiencer().equals("Patient")) continue;
			
			if(cm.getNormTarget().equals("Location") 
					|| cm.getNormTarget().equals("Location2")) {
				//Not correct!
//				if(cm.getCoveredText().equals("foot") && (!isExclusion(jcas, cm))) {
//					System.out.println("in foot cond");
//					location1and2.add(cm);
//				}
				if(cm.getCoveredText().equals("foot")) {
					if(!isExclusion(jcas, cm)) {
						location1and2.add(cm);
					}
				}
				else {
					location1and2.add(cm);
				}
			}
			
			if(cm.getNormTarget().equals("Location2")) location2.add(cm);
			else if(cm.getNormTarget().equals("Location2-1")) location2_1.add(cm);
			else if(cm.getNormTarget().equals("FirstDiag")) firstDiag.add(cm);
			else if(cm.getNormTarget().equals("FirstDiag2")) firstDiag2.add(cm);
			else if(cm.getNormTarget().equals("FirstDiagOcclusion")) {
				if(!cm.getCoveredText().equals("pad")) //pad is usually NOT disease PAD
					firstDiagOcclusion.add(cm);
			}
			else if(cm.getNormTarget().equals("SecondDiag")) {
				if(!isExclusion(jcas, cm))
					secondDiag.add(cm);
			}
			else if(cm.getNormTarget().equals("ThirdDiag")) {
				if(!cm.getCoveredText().equals("pad")) //pad is usually NOT disease PAD
					thirdDiag.add(cm);
			}
			//else if(cm.getNormTarget().equals("FirstExcl")) firstExcl.add(cm);
			//else if(cm.getNormTarget().equals("SecondExcl")) secondExcl.add(cm);
			//else if(cm.getNormTarget().equals("AmputationExcl")) amputationExcl.add(cm);
			else if(cm.getNormTarget().equals("Uncertain")) uncertain.add(cm);
			else if(cm.getNormTarget().startsWith("ABI")) {
				String[] f = cm.getNormTarget().split("~");
				Double abi = 100.0;
				if(f.length==2) {
					abi = Double.parseDouble(f[1]);
				}
				else if(f.length==3) {
					abi = Double.parseDouble(f[1]);
					abi = abi>Double.parseDouble(f[2]) ? abi : Double.parseDouble(f[2]);
				}
				else {
					System.out.print("Incorrect ABI norm");
					System.exit(1);
				}
				
				if(abi<=0.9) firstDiag.add(cm);
			}
			else {}	
		}
		
		location1and2and2_1.addAll(location1and2);
		location1and2and2_1.addAll(location2_1);
			
		List<Info> definite = new ArrayList<Info>();
		List<Info> probable = new ArrayList<Info>();
		List<Info> possible = new ArrayList<Info>();
		
		//---WINDOW LEVEL---
		//For firstDiagOcclusion
		if(location1and2and2_1.size()>0) {
			//definite & possible
			for(ConceptMention diag : firstDiagOcclusion) {
				int[] span = getWindow(jcas, diag, senDist);
				for(ConceptMention loc : location1and2and2_1) {
					if(loc.getBegin()>=span[0] && loc.getEnd()<=span[1]) {
						//definite
						Info info = new Info(
								jcas.getDocumentText().substring(span[0], span[1]), diag, loc, null);		
						definite.add(info);
							
						//possible
						Boolean isPos=false;
						for(ConceptMention unc : uncertain) {
							if(isDocLevelUncert
									|| (unc.getBegin()>=span[0] && unc.getEnd()<=span[1]) ) {
								info.uncertain = unc;
								possible.add(info);
								isPos=true;
							}
						}
						//remove definite if possible
						if(isPos) {
							definite.remove(definite.size()-1); 
						}			
					}
				}								
			}
		}
		
		//for firstDiag2
		if(location2.size()>0) {
			//definite & possible
			for(ConceptMention diag : firstDiag2) {
				int[] span = getWindow(jcas, diag, senDist);
				for(ConceptMention loc : location2) {
					if(loc.getBegin()>=span[0] && loc.getEnd()<=span[1]) {
						//definite
						Info info = new Info(
								jcas.getDocumentText().substring(span[0], span[1]), diag, loc, null);		
						definite.add(info);
							
						//possible
						Boolean isPos=false;
						for(ConceptMention unc : uncertain) {
							if(isDocLevelUncert
									|| (unc.getBegin()>=span[0] && unc.getEnd()<=span[1]) ) {
								info.uncertain = unc;
								possible.add(info);
								isPos=true;
							}
						}
						//remove definite if possible
						if(isPos) {
							definite.remove(definite.size()-1); 
						}			
					}
				}								
			}
		}
		
		//For firstDiag
		if(location1and2.size()>0) {
			//definite & possible
			for(ConceptMention diag : firstDiag) {
				int[] span = getWindow(jcas, diag, senDist);
				for(ConceptMention loc : location1and2) {
					if(loc.getBegin()>=span[0] && loc.getEnd()<=span[1]) {
						//definite
						Info info = new Info(
								jcas.getDocumentText().substring(span[0], span[1]), diag, loc, null);		
						definite.add(info);
							
						//possible
						Boolean isPos=false;
						for(ConceptMention unc : uncertain) {
							if(isDocLevelUncert
									|| (unc.getBegin()>=span[0] && unc.getEnd()<=span[1]) ) {
								info.uncertain = unc;
								possible.add(info);
								isPos=true;
							}
						}
						//remove definite if possible
						if(isPos) {
							definite.remove(definite.size()-1); 
						}			
					}
				}								
			}
			
			//for secondDiag
			//probable & possible
			for(ConceptMention diag : secondDiag) {
				int[] span = getWindow(jcas, diag, senDist);
				//int[] span = getSectionWindow(jcas, diag);
				for(ConceptMention loc : location1and2) {
					if(loc.getBegin()>=span[0] && loc.getEnd()<=span[1]) {
						//probable
						Info info = new Info(
								jcas.getDocumentText().substring(span[0], span[1]), diag, loc, null);	
						probable.add(info);
						
						//possible
						Boolean isPos=false;
						for(ConceptMention unc : uncertain) {
							if(isDocLevelUncert
									|| (unc.getBegin()>=span[0] && unc.getEnd()<=span[1])) {
								info.uncertain = unc;
								possible.add(info);
								isPos=true;
							}
						}
						//remove probable if possible
						if(isPos) {
							probable.remove(probable.size()-1); 
						}			
					}
				}								
			}
			
			//for thirdDiag
			//possible
			
			for(ConceptMention diag : thirdDiag) {
				int[] span = getWindow(jcas, diag, senDist);
				//int[] span = getSectionWindow(jcas, diag);
				for(ConceptMention loc : location1and2) {
					if(loc.getBegin()>=span[0] && loc.getEnd()<=span[1]) {
						for(ConceptMention unc : uncertain) {
							if(isDocLevelUncert
									|| (unc.getBegin()>=span[0] && unc.getEnd()<=span[1])) {
								Info info = new Info(
										jcas.getDocumentText().substring(span[0], span[1]), diag, loc, unc);	
								possible.add(info);
							}
						}			
					}
				}								
			}
			
			
		}
		
//		//-----possible	
//		String allThirdDiags = "";
//		int thirdDiagCnt = 0;
//		for(ConceptMention diag : thirdDiag) {
//			thirdDiagCnt++;
//			if(thirdDiagCnt==1) {
//				allThirdDiags = diag.getCoveredText();
//			}
//			else {
//				allThirdDiags += "`" +diag.getCoveredText();
//			}
//		}		
//		if(thirdDiagCnt>=2) {
//			Info info = new Info(allThirdDiags, null, null, null);	
//			possible.add(info);
//		}
//		//-----
		
		//---DOC LEVEL---
		if(definite.size()>0) {
			printAnnotations(jcas, "DEFINITE", definite);
		}
		else if(possible.size()>0) {
			printAnnotations(jcas, "POSSIBLE", possible);
		}
		else if(probable.size()>0) {
			printAnnotations(jcas, "PROBABLE", probable);
		}
		else {
			List<Info> empty = new ArrayList<Info>();
			printAnnotations(jcas, "NA", empty);
		}
		
	}
	
	private int[] getSectionWindow(JCas jcas, ConceptMention cm) {
		int[] ret = {-1, -1};
		ret[0] = cm.getSentence().getSegment().getBegin();
		ret[1] = cm.getSentence().getSegment().getEnd();
		
		return ret;
	}
	
	/**
	 * Return the span of the window: 
	 * 	- within the given section
	 *  - concept mention +/- dist sentence
	 * 
	 * @param jcas
	 * @param cm	diag concept mention
	 * @param dist	sentence distance
	 * @return
	 */
	private int[] getWindow(JCas jcas, ConceptMention cm, int dist) {
		//ad-hoc -> use the same sentence for the following cases
		if(cm.getCoveredText().toLowerCase().startsWith("stenos")
				|| cm.getCoveredText().toLowerCase().matches("stent")
				|| cm.getCoveredText().toLowerCase().matches("ischemia")) {
			dist = 0;
		}
		
		//section:sentenceNum (note that sentenceNum starts from 0 for each section)
		String[] fields = cm.getSentence().getId().split(":");
		String section = fields[0];
		int id = Integer.parseInt(fields[1]);
		
		FSIterator<? extends Annotation> senIter = jcas.getAnnotationIndex(
				Sentence.type).iterator();
		String lastSenID="";
		while (senIter.hasNext()) {
			Sentence sen = (Sentence) senIter.next();
			if(sen.getId().split(":")[0].equals(section)) {
				lastSenID = sen.getId();
			}
		}
		int lastSenNum = Integer.parseInt(lastSenID.split(":")[1]);
		
		//int senSize = jcas.getAnnotationIndex(Sentence.type).size();
		int beginSenNum = (id-dist)<0 ? 0 : (id-dist);
		int endSenNum = (id+dist)>lastSenNum ? lastSenNum : (id+dist);
		String beginSenID = section + ":" + beginSenNum;
		String endSenID = section + ":" + endSenNum;
		int[] ret = {-1, -1};
		
		//bound within a given sentence distance
		senIter = jcas.getAnnotationIndex(
				Sentence.type).iterator();		
		while (senIter.hasNext()) {
			Sentence sen = (Sentence) senIter.next();
			if (beginSenID.equals(sen.getId())) {
				ret[0] = sen.getBegin();
			}
			if (endSenID.equals(sen.getId())) {
				ret[1] = sen.getEnd();
				break;
			}			
		}
		
		if(ret[0]==-1 || ret[1]==-1) {
			System.out.println("Incorrect text window");
			System.exit(1);
		}
				
		//bound within the line
		//- do not use it because there are list-type description
		// eg)
		// #1 Peripheral vascular disease with two Class B findings 
		// #2 Callus formation both feet
		// 
//		int lineBegin = -1;
//		int lineEnd = -1;
//		FSIterator<? extends Annotation> ntIter = jcas.getAnnotationIndex(
//				NewlineToken.type).iterator();
//		int prevEnd = -1;
//		while(ntIter.hasNext()) {
//			NewlineToken nt = (NewlineToken) ntIter.next();
//			if(prevEnd==-1) {
//				if(cm.getBegin()>=0 && cm.getEnd()<=nt.getBegin()) {
//					lineBegin = 0;
//					lineEnd = nt.getBegin();
//					break;
//				}
//			}
//			else {
//				if(cm.getBegin()>=prevEnd && cm.getEnd()<=nt.getBegin()) {
//					lineBegin = prevEnd;
//					lineEnd = nt.getBegin();
//					break;
//				}
//			}			
//			prevEnd = nt.getEnd();
//		}
//		
//		if(lineBegin==-1 || lineEnd==-1) {
//			System.out.println("Incorrect line offset");
//			System.exit(1);
//		}
//		
//		if(ret[0]<lineBegin) ret[0] = lineBegin;
//		if(ret[1]>lineEnd) ret[1] =lineEnd;
			
		//bound within the section
		int secBegin = cm.getSentence().getSegment().getBegin();
		int secEnd = cm.getSentence().getSegment().getEnd();

		if(ret[0]<secBegin) ret[0] = secBegin;
		if(ret[1]>secEnd) ret[1] =secEnd;
		
		return ret;
	}
	
	private void printAnnotations(JCas jcas, String cls, List<Info> infos) {
		JFSIndexRepository indexes = jcas.getJFSIndexRepository();
		FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);
		File outFile = null;
		File inFile = null;
		String outFileName = "";
		if (docIterator.hasNext()) {
			Document docAnn = (Document) docIterator.next();
			String fileLoc =docAnn.getFileLoc(); 
			try {
				inFile = new File(new URL(fileLoc).getPath());
				outFileName = inFile.getName();
				outFile = new File(mOutputDir, outFileName+".ann");
			} catch (MalformedURLException e1) {
			}
		}
		if (outFile == null) {
			outFile = new File(mOutputDir, "doc" + mDocNum++);
		}

		String toprint = "";
		String infoStr = "";
		int cnt=0;
		for(Info i : infos) {
			if(cnt>0) infoStr += "~~";
			if(cls.equals("POSSIBLE")) {
				if(i.diag==null) {
					infoStr += "MULTIPLE THIRD DIAG" + "::" + "null"
							+ "::" + "null" + "::" + i.text;
				}
				else {
					infoStr += i.diag.getCoveredText() + "::" + i.location.getCoveredText()
							+ "::" + i.uncertain.getCoveredText() + "::" + i.text;
				}
			}
			else {
				infoStr += i.diag.getCoveredText() + "::" + i.location.getCoveredText()
							+ "::" + null + "::" + i.text;
			}

			cnt++;
		}
		
		infoStr = infoStr.replaceAll("\r", "").replaceAll("\n", " ");
		toprint = outFileName + "|" + cls + "|" + infoStr + "\n";
		
		try {
			//BufferedWriter bf = new BufferedWriter(new FileWriter(outFile));
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "output.txt";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam,true));

			bf.append(toprint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}		
	}
	
	// print out match and concept mention	
	private void printAnnotationsInline(JCas jcas){	    
		JFSIndexRepository indexes = jcas.getJFSIndexRepository();
		FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);
		File outFile = null;
		File inFile = null;
		String outFileName = "";
		if (docIterator.hasNext()) {
			Document docAnn = (Document) docIterator.next();
			String fileLoc =docAnn.getFileLoc(); 
			try {
				inFile = new File(new URL(fileLoc).getPath());
				outFileName = inFile.getName();
				outFile = new File(mOutputDir, outFileName+".ann");
			} catch (MalformedURLException e1) {
			}
		}
		if (outFile == null) {
			outFile = new File(mOutputDir, "doc" + mDocNum++);
		}

	    //print out results
	    String toprint = "";	    
	    FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(
	    		ConceptMention.type).iterator();
	    
		while (cmIter.hasNext()) {
			ConceptMention cm = (ConceptMention) cmIter.next();			
			
			//text|beginOffset|endOffset|variable|value
			toprint	+= outFileName+"|"+cm.getCoveredText()+"|"+cm.getBegin()+"|"+cm.getEnd()+"|"
						+cm.getNormTarget()+"\n";
		}
		
		try {
			//BufferedWriter bf = new BufferedWriter(new FileWriter(outFile));
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "output.txt";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam,true));

			bf.append(toprint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}		
	}
	
	private boolean isExclusion(JCas jcas, ConceptMention cm) {
		//\nAmputation. No\n
		if(cm.getCoveredText().toLowerCase().equals("amputation")) {
			//System.out.println("Found amputation");
			if(jcas.getDocumentText().substring(cm.getEnd()).matches(".\\s+No\\n(.|\\s)*"))
				return true;
		}
		
		//\nHistory of foot pain.  No\n
		if(cm.getCoveredText().toLowerCase().equals("foot")) {
			//System.out.println("Found foot");
			if(jcas.getDocumentText().substring(cm.getEnd()).matches("\\s+pain.\\s+No\\n(.|\\s)*"))
				return true;
		}
		
		return false;
	}

}
