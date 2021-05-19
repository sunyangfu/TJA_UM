package org.ohnlp.medtagger.ie.cc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.lang.String;

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

import org.ohnlp.medtagger.ie.cc.PADOutputWriter.Info;
import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.typesystem.type.structured.Document;
import org.ohnlp.typesystem.type.syntax.NewlineToken;
import org.ohnlp.typesystem.type.textspan.Sentence;


/**
 * 
 * THA Fixation Pt-level output
 * Process operation notes to classify a fixation category to cement, hybrid, reverse hybrid, and uncement
 * @author Sunghwan Sohn, Sunyang Fu
 *
 */
public class TJAFracture_hyOutputWriter extends CasConsumer_ImplBase {
	class Info {
		String date;
		String docName;
		String concept;
		String norm;
		int conBegin;
		int conEnd;
		String sentence;
		String senId;
		String section;
		String negation;
		
		public Info() {}
		
		public Info(String dt, String docnam, String con, String nm, int cb, int ce,
				String sen, String sid, String sec, String neg) {
			date = dt;
			docName = docnam;
			concept = con;
			norm = nm;
			conBegin = cb;
			conEnd = ce;
			sentence = sen;
			senId = sid;
			section = sec;
			negation = neg;
		}
	}
	
	public static final String DELIM = "|";
	public static final String PARAM_OUTPUTDIR = "OutputDir";
	public static final String PARAM_LASTFUDATEFILE = "LastFuDateFile";
	private File mOutputDir; 
	private int mDocNum;
	//key=mcn, val=map of (key=date (yyyymmdd), val=list of Info"; ordered by "date")
	private TreeMap<String, TreeMap<String, ArrayList<Info>>> ptSumMap;
	private Set<String> diagSections; 
	private Set<String> exclSections; 
	private Map<String, String> lastFuDate; //key=mcn (assume unique mcn), val=index date (yyyymmdd)
	private Set<String> noMCN; 
	
	public void initialize() throws ResourceInitializationException {
		mDocNum = 0;
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		if (!mOutputDir.exists()) {
			mOutputDir.mkdirs();
		} 
		ptSumMap = new TreeMap<String, TreeMap<String, ArrayList<Info>>>();
		diagSections = new HashSet<String>();
		exclSections = new HashSet<String>();
		noMCN = new HashSet<String>();	
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

		JFSIndexRepository indexes = jcas.getJFSIndexRepository();
		FSIterator<TOP> docIterator = indexes.getAllIndexedFS(Document.type);
		String docName = "";
		String mcn = "";
		String noteDate = "";
		
		if (docIterator.hasNext()) {
			Document docAnn = (Document) docIterator.next();
			String fileLoc =docAnn.getFileLoc(); 
			String[] f = fileLoc.split("/");
			docName = f[f.length-1]; 
			System.out.println("---"+docName+" processing---");
			mcn = docName.split("_")[0]; //8 digits
			//op note name: 00803679_12490165_1_SURG_Operative-Report_1995-09-18.txt
			//TODO add "operation date" in the file name
//			noteDate = docName.split("_")[5].replaceAll("-", "").replaceAll(".txt", ""); //yyyymmdd
		}

		boolean skip = false;
		/*
		String lstDate = lastFuDate.get(mcn);
		if(lstDate==null) {
			if(!noMCN.contains(mcn)) {
				System.out.println(mcn + " not in the lastfudx file"); //gold standard file
				noMCN.add(mcn);
			}
			skip = true;
		}	
		else { 
			if(getDateDiff(lstDate,noteDate)>0) {
				skip = true;
				//System.out.println("  "+mcn+"|"+lstDate+"|"+noteDate);
			}
		}
		*/
		if(!skip) { 
			//System.out.println("  "+mcn+"|"+lstDate+"|"+noteDate);
			List<Info> infoInDoc = new ArrayList<Info>();
			FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(
					ConceptMention.type).iterator();
			while (cmIter.hasNext()) {
				ConceptMention cm = (ConceptMention) cmIter.next();

				//skip exclusion sections
				if(exclSections.contains(cm.getSentence().getSegment().getId())) continue;

				//check assertion
//				if(!cm.getCertainty().equals("Positive")) {
//					boolean isFound = cm.getSentence().getCoveredText().indexOf("No.") !=-1? true: false;
//					if (!isFound) continue;
//				}
				//if(cm.getStatus().equals("FamilyHistoryOf")) continue; //use Present and HistoryOf
				//if(cm.getStatus().equals("HistoryOf")) continue;
				if(!cm.getExperiencer().equals("Patient")) continue; 
		
				Info i = new Info(noteDate, docName, cm.getCoveredText(), cm.getNormTarget(),
						cm.getBegin(), cm.getEnd(), cm.getSentence().getCoveredText(),
						cm.getSentence().getId(), cm.getSentence().getSegment().getId(), cm.getCertainty());

				infoInDoc.add(i);

				//dateInfoMap: key=date, val=list of Info
				TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
				if(dateInfoMap==null) dateInfoMap = new TreeMap<String, ArrayList<Info>>();
				ArrayList<Info> infoList =  dateInfoMap.get(noteDate);
				if(infoList==null) infoList = new ArrayList<Info>();
				infoList.add(i);
				dateInfoMap.put(noteDate, infoList);
				ptSumMap.put(mcn, dateInfoMap);
			}		

			//if no concept found for the mcn, add null (to get mcn w/o concept)
			if(ptSumMap.get(mcn)==null) ptSumMap.put(mcn, null);

			printAnnotations(infoInDoc);
		}
	}
				
	private boolean isInSameSection(Info in1, Info in2) {
		if(in1.docName.equals(in2.docName) && in1.section.equals(in2.section))
			return true;
		
		return false;
	}
	
	private boolean isInSameSentence(Info in1, Info in2) {
		if( in1.docName.equals(in2.docName) && in1.senId.equals(in2.senId) )
			return true;
		
		return false;
	}
	
	/**
	 * 
	 * @param date1 (yyyyMMdd)
	 * @param date2 (yyyyMMdd)
	 * @return difference in days (date2 - date1)
	 */
	private int getDateDiff(String date1, String date2) {
		 SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd"); 
		 int diffInDays=0;
		 
		 try {
			 Date d1 = format.parse(date1);
			 Date d2 = format.parse(date2);
			 diffInDays = (int)( (d2.getTime() - d1.getTime()) / (1000*60*60*24) );
		 } catch (ParseException e) {
			 e.printStackTrace();
		 }    
 
	     return diffInDays;
	}
	
	private void printAnnotations(List<Info> info) {
		String toPrint = "";
		for(Info i : info) {
			//toPrint += i.docName+"|"+i.concept+"|"+i.norm+"|"+i.section+"|"+i.sentence+"\n";
			//docName|conceptText|conceptBegin|conceptEnd|Norm|section|sentence|sentenceID
			toPrint += i.docName+"|"+i.concept+"|"+i.conBegin+"|"+i.conEnd+"|"+i.norm+"|"
						+i.section+"|"+i.sentence+"|"+i.senId+"\n";
		}
		
		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
							+ File.separator + "Bearing.out";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam, true));
			bf.append(toPrint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	/**
	 * Return evidences with satisfied criteria for a given asthma status
	 * @param criteriaMet satisfied criteria delimited by ":" for a given status
	 * @param criteria a map of all satisfied criteria
	 * @return
	 */
	private String getEvidence(String criteriaMet, Map<String, List<Info>> criteria) {
		String ret = criteriaMet+" ";
		String[] crit = criteriaMet.split(":");
		
		for(int i=0; i<crit.length; i++) {
			List<Info> info = criteria.get(crit[i]);
			if(info==null) {
				System.out.println(crit[i] + " has NO Info");
				System.exit(1);
			}
			ret += "<"+crit[i]+">";
			
			int cnt=0;
			for(Info in : info) {
				if(cnt>0) ret += "~~";
				ret += in.docName+"::"+in.section+"::"+in.concept+"::"+in.sentence;
				cnt++;
			}	
		}
		
		return ret;
	}
	
	/**
	 * Return the earliest date that satisfies all the criteria
	 * @param criteriaMet
	 * @param criteria
	 * @return
	 */
	private String getIndexDate(String criteriaMet, Map<String, List<Info>> criteria) {
		SortedSet<String> date = new TreeSet<String>();
		
		String[] crit = criteriaMet.split(":");
		for(int i=0; i<crit.length; i++) {
			List<Info> info = criteria.get(crit[i]);
			if(info==null) {
				System.out.println(crit[i] + " has NO Info");
				System.exit(1);
			}
			//info includes "all" related conditions until it satisfies the criteria
			//so the last element will be the earliest date 
			date.add(info.get(info.size()-1).date);
		}
		
		//date is in order of earliest to latest 
		return date.last();
	}
	
	/**
	 * If isAny=true, return the earliest date that satisfies any one of criteria 
	 * If isAny=false, return the earliest date that satisfies all the criteria
	 * @param criteriaMet
	 * @param criteria
	 * @return
	 */
	private String getIndexDate(String criteriaMet, Map<String, List<Info>> criteria, boolean isAny) {
		SortedSet<String> date = new TreeSet<String>();
		
		String[] crit = criteriaMet.split(":");
		for(int i=0; i<crit.length; i++) {
			List<Info> info = criteria.get(crit[i]);
			if(info==null) {
				System.out.println(crit[i] + " has NO Info");
				System.exit(1);
			}
			//info includes "all" related conditions until it satisfies the criteria
			//so the last element will be the earliest date that satisfies the criteria
			date.add(info.get(info.size()-1).date);
		}
		
		//date is in order of earliest to latest 
		if(isAny)
			return date.first();
		else
			return date.last();
	}
	
	/**
	 * This method calls once after completion of the process of all documents
	 * Perform a pt-level asthma status based on the collection of doc-level information in ptSumMap
	 */
	public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException {
		super.collectionProcessComplete(arg0);
		
					
		String toPrint = "";
		//For each patient iterate all notes
		for(String mcn : ptSumMap.keySet()) {
			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
			boolean isfra = false;
			boolean isAG = false;
			boolean isAL = false;
			boolean isB1 = false;
			boolean isB2 = false;
			boolean isB3 = false;
			boolean isC = false;
			
			boolean isB3_ = false;
			boolean isB2_ = false;
			boolean isB1_ = false;

			//mcn|category|index_date|evidence

			if(dateInfoMap==null) { 
				//System.out.println(mcn + " has no concept");
				toPrint += mcn+DELIM+"NA"+DELIM+DELIM+"\n";
				continue;
			}

			//process per date - assume one operation per day
			for(String date : dateInfoMap.keySet()) {				
				String sent = "";
				ArrayList<Info> Infos = dateInfoMap.get(date);
				for(Info i : Infos) {
					//Direct mention
					if(i.norm.equals("fra")) {
						isfra = true;
						sent += "<fracture>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}	

					if(i.norm.equals("AG")) {
						isAG = true;
						sent += "<AG>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					if(i.norm.equals("AL")) {
						isAL = true;
						sent += "<AL>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					if(i.norm.equals("B1")) {
						isB1 = true;
						sent += "<B1>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					if(i.norm.equals("B2")) {
						isB2 = true;
						sent += "<B2>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					if(i.norm.equals("B3")) {
						isB3 = true;
						sent += "<B3>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					if(i.norm.equals("C")) {
						isC = true;
						sent += "<C>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}
					
					if(i.norm.equals("Bone") && i.negation.equals("Positive")) {
						isB3_ = true;
						sent += "<B3_>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}	
					
					if(i.norm.equals("Bone") && i.negation.equals("Negated")) {
						isB2_ = true;
						sent += "<B2_>" + "<"+ i.senId + ">" + i.sentence+"~"; 
					}		
					
					if(i.negation.equals("Positive") && i.norm.equals("Loose")){
						isB2_ = true;
						sent += "<B2_>" + "<"+ i.senId + ">" + i.sentence+"~";						
					}
					
					if(i.negation.equals("Negated") && i.norm.equals("Loose")){
						isB1_ = true;
						sent += "<B1_>" + "<"+ i.senId + ">" + i.sentence+"~";
					}
				}
				//
				String fxt = "";
				if(isfra) {
					if (isB1_) fxt = "B1";
					if (isB3_) fxt = "B3";
					if (isB2_) fxt = "B2";
					if (isAG) fxt = "AG"; 
					if (isAL) fxt = "AL";
					if (isB1) fxt = "B1"; 
					if (isB2) fxt = "B2"; 
					if(isB3) fxt = "B3";
					if(isC) fxt = "C";
					
					//
				}; 
				toPrint += mcn+DELIM+fxt+DELIM+date+DELIM+sent+"\n";
			}
	
		}

		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "THA_fixation.out";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam));
			bf.append(toPrint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}

