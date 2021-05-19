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
 * Pt-level output
 * 	 asthma status cannot be determined in a doc-level because it requires multiple 
 *   evidences across documents
 * @author Sunghwan Sohn
 *
 * TODO create feature vectors for ML (do it here or in a separated annotator?)
 */
public class AsthmaPACPtLevelOutputWriter extends CasConsumer_ImplBase {
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
		
		public Info() {}
		
		public Info(String dt, String docnam, String con, String nm, int cb, int ce,
				String sen, String sid, String sec) {
			date = dt;
			docName = docnam;
			concept = con;
			norm = nm;
			conBegin = cb;
			conEnd = ce;
			sentence = sen;
			senId = sid;
			section = sec;
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
		//diagSections.add("20112"); //only title items should be used - they are listed in 20113
		diagSections.add("20113");
		
		exclSections = new HashSet<String>();
		exclSections.add("20108"); //social history
		exclSections.add("20109"); //family history
		exclSections.add("20107"); //past medical/surgical history
		exclSections.add("20104"); //current medication
		exclSections.add("20105"); //allergy
		exclSections.add("20122"); //adverse reactions
		exclSections.add("20163"); //patient instruction
		exclSections.add("20187"); //D/C instruct
		exclSections.add("20115"); //Special Instructions
		exclSections.add("20163"); //Instructions to Include Post-Procedure
		exclSections.add("20176"); //Instructions for continuing care Hospital Summary
		exclSections.add("20129"); //Follow Up Agreements
		exclSections.add("20161"); //Follow-Up Letter Post-Procedure
		
		noMCN = new HashSet<String>();
		
		lastFuDate = new HashMap<String, String>();
		try {
			String fnam = (String) getConfigParameterValue(PARAM_LASTFUDATEFILE); 
			BufferedReader br = new BufferedReader(new FileReader(fnam));
			String line="";
			//clinic\tlastfudate\tstatus
			while((line=br.readLine()) != null) {
				if(line.startsWith("//")) continue;
				String[] str = line.split("\\t");
				lastFuDate.put(String.format("%08d", Integer.parseInt(str[0])), str[1]);
			}	
			br.close();
		} catch (IOException e1) {
			e1.printStackTrace();
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
			//System.out.println("---"+docName+" processing---");
			mcn = docName.split("_")[0]; //8 digits
			//docName: 06000125_1033374427_1_SUP_2003-12-05.txt - previous format (original birth cohort)
			//noteDate = docName.split("_")[4].replaceAll("-", "").replaceAll(".txt", ""); //yyyymmdd
			//docName: 05494407_76414819_2_MIS_Ped-Comm-NW_2004-07-27.txt - new format!!
			noteDate = docName.split("_")[5].replaceAll("-", "").replaceAll(".txt", ""); //yyyymmdd
		}

		boolean skip = false;
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
				if(!cm.getCertainty().equals("Positive")) continue;
				if(cm.getStatus().equals("FamilyHistoryOf")) continue; //use Present and HistoryOf
				
				//TODO test 
				//allow HistoryOf only for WHEEZE and ASTHMA
				if( (!cm.getNormTarget().equals("WHEEZE"))
						&& (!cm.getNormTarget().equals("ASTHMA")) )
					if(cm.getStatus().equals("HistoryOf")) continue;
									
				if(!cm.getExperiencer().equals("Patient")) continue; 

				//ad-hoc negation
				if(cm.getSentence().getCoveredText().matches("(\\s+)?- ?No .*?")
						|| cm.getSentence().getCoveredText().matches("(\\s+)?No ?: .*?")
						|| cm.getSentence().getCoveredText().matches("(\\s+)?- ?Not .*?"))
					continue;
				
				Info i = new Info(noteDate, docName, cm.getCoveredText(), cm.getNormTarget(),
						cm.getBegin(), cm.getEnd(), cm.getSentence().getCoveredText(),
						cm.getSentence().getId(), cm.getSentence().getSegment().getId());

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
	
	private ArrayList<Info> getPhDiag(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		
		//check physician diagnosis asthma
		//start from the earliest date
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				if(!diagSections.contains(i.section)) continue;				
				if(i.norm.equals("ASTHMA")) {
					ret.add(i);
					return ret;
				}				
			}
			
			boolean hasCOPD = false;
			for(Info i : dateInfo.get(date)) {
				if(!diagSections.contains(i.section)) continue;				
				if(i.norm.equals("COPD")) {
					ret.add(i);
					hasCOPD =  true;
				}
			}
			
			if(hasCOPD) {
				for(Info i : dateInfo.get(date)) {
					if(!diagSections.contains(i.section)) continue;				
					if(i.norm.equals("BRONCHOSPASM")) {
						ret.add(i);
						return ret;
					}
				}
			}
		}
		
		//TODO test!! 
		//check recurrent bronchiolitis (2 and more of bronchiolitis)
		//if within 3 wks treat it as the same episode
		ArrayList<Info> ret2 = new ArrayList<Info>();
		
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				if(!diagSections.contains(i.section)) continue;		
				if(i.norm.equals("BRONCHIOLITIS")) { 
					ret2.add(i);
				}
			}
			
			//check if occurs 2 times or more (at least 3 weeks apart within 3 years)
			for(int i=0; i<ret2.size(); i++) {
				for(int j=i+1; j<ret2.size(); j++) { 
					int interval = getDateDiff(ret2.get(i).date, ret2.get(j).date);
					if(interval<=21) continue; 
					if(interval>1095) break; 
					
					return ret2;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Return criteria 1 conditions if there exist wheeze and (cough|dyspnea)
	 * for the same date, OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getCriteria1(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		boolean metCriteria = false; 
		
		//For the same date
		for(String date : dateInfo.keySet()) {
			boolean hasWheeze = false;			
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("WHEEZE")) { 
					hasWheeze = true;
					ret.add(i);
				}
			}
			
			if(hasWheeze) { 
				for(Info i : dateInfo.get(date)) {
					if(i.norm.equals("COUGH")
							|| i.norm.equals("DYSPNEA")) { 
						metCriteria = true;	
						ret.add(i);
					}
				}
			}
			
			if(metCriteria) return ret;
		}
		
		return null;
	}
	
	private ArrayList<Info> getCriteria2(TreeMap<String, ArrayList<Info>> dateInfo) {		
		ArrayList<Info> ret = new ArrayList<Info>();
		List<String> metCondDate = new ArrayList<String>();
		//For the same date
		for(String date : dateInfo.keySet()) {
			boolean hasWheeze = false;			
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("WHEEZE")) { 
					hasWheeze = true;
					ret.add(i);
				}
			}
			
			if(hasWheeze) { 
				for(Info i : dateInfo.get(date)) {
					if(i.norm.equals("COUGH")
							|| i.norm.equals("DYSPNEA")) { 
						metCondDate.add(date);
						ret.add(i);
					}
				}
			}
			
			//check if occurs 2 times or more (at least 3 weeks apart within 3 years)
			for(int i=0; i<metCondDate.size(); i++) {
				for(int j=i+1; j<metCondDate.size(); j++) { 
					int interval = getDateDiff(metCondDate.get(i), metCondDate.get(j));
					if(interval<=21) continue; 
					if(interval>1095) break; 
					
					return ret;
				}
			}
		}
		
		return null;
	}
	
	private ArrayList<Info> getCriteria3(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		Set<String> metCond = new HashSet<String>();
		
		for(String date : dateInfo.keySet()) {	
			List<Info> night = new ArrayList<Info>();
			List<Info> antigen = new ArrayList<Info>();
			List<Info> pulmonaryTest = new ArrayList<Info>();

			for(Info i : dateInfo.get(date)) {					
				if(i.norm.equals("NIGHTTIME")) 
					night.add(i);
				else if(i.norm.equals("EXPOSURE_TO_ANTIGEN")) 
					antigen.add(i);
				else if(i.norm.equals("PULMONARY_TEST")) 
					pulmonaryTest.add(i);			
			}
			
			for(Info i : dateInfo.get(date)) {									
				//TODO implement smoking status
				
				if(i.norm.equals("COUGH") || i.norm.equals("WHEEZE")) {
					for(Info info : night) {
						if(isInSameSection(info, i)) {
							ret.add(i);
							ret.add(info);
							metCond.add("NIGHTTIME");
							break;
						}
					}
				}
				else if(i.norm.equals("NASAL_POLYPS")) {
					ret.add(i);
					metCond.add("NASAL_POLYPS");
				}
				else if(i.norm.equals("EOSINOPHILIA_HIGH")) {
					ret.add(i);
					metCond.add("EOSINOPHILIA_HIGH");
				}
				else if(i.norm.equals("POSITIVE_SKIN")) {
					ret.add(i);
					metCond.add("POSITIVE_SKIN");
				}
				else if(i.norm.equals("SERUM_IGE_HIGH")) {
					ret.add(i);
					metCond.add("SERUM_IGE_HIGH");
				}
				else if(i.norm.equals("HAY_FEVER")) {
					if(diagSections.contains(i.section)) { //modified Aug-3-2015
						ret.add(i);
						metCond.add("HAY_FEVER");
					}
				}
				else if(i.norm.equals("INFANTILE_ECZEMA")) {
					if(diagSections.contains(i.section)) { //modified Aug-3-2015
						ret.add(i);
						metCond.add("INFANTILE_ECZEMA");
					}
				}				
				else if(i.norm.equals("COUGH") || i.norm.equals("WHEEZE") 
						|| i.norm.equals("DYSPNEA")) {
					for(Info info : antigen) {
						if(isInSameSentence(info, i)) {
							ret.add(i);
							ret.add(info);
							metCond.add("EXPOSURE_TO_ANTIGEN");
							break;
						}
					}
				}
				//TODO add pulmonary function test	
				
				else if(i.norm.equals("FAVORABLE_RESPONSE_BRONCHODILATOR")) {
					ret.add(i);
					metCond.add("FAVORABLE_RESPONSE_BRONCHODILATOR");
				}					
			}
			
			if(metCond.size()>=2) return ret;
		}
		
		return null;
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
			toPrint += i.docName+"|"+i.concept+"|"+i.norm+"|"+i.section+"|"+i.sentence+"\n";
		}
		
		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
							+ File.separator + "asthma.out";
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
		
		//check data
//		for(String s : ptSumMap.keySet()) {
//			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(s);
//			for(String ss : dateInfoMap.keySet()) {
//				for(Info info : dateInfoMap.get(ss)) {
//					System.out.println("collection:"+info.date+"|"+info.docName+"|"+info.concept
//							+"|"+info.norm+"|"+info.conBegin+"|"+info.conEnd+"|"+info.sentence
//							+"|"+info.senId+"|"+info.section);
//				}
//			}
//		}
					
		String toPrint = "";
		for(String mcn : ptSumMap.keySet()) {
			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
			boolean isPhDiag = false;
			boolean metCriteria1 = false;
			boolean metCriteria2 = false;
			boolean metCriteria3 = false;
			
			if(dateInfoMap==null) { 
				//System.out.println(mcn + " has no concept");
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
				continue;
			}

			//check each criteria
			//Info is the earliest satisfied 
			List<Info> phDiag = getPhDiag(dateInfoMap);
			List<Info> criteria1 = getCriteria1(dateInfoMap);
			List<Info> criteria2 = getCriteria2(dateInfoMap);
			List<Info> criteria3 = getCriteria3(dateInfoMap);
			
			//key=criterion, val=list of Info
			Map<String, List<Info>> criteria = new HashMap<String, List<Info>>();
			
			if(phDiag != null) {
				criteria.put("PhD", phDiag);
				isPhDiag = true;
			}
			if(criteria1 != null) {
				criteria.put("C1", criteria1);
				metCriteria1 = true;
			}
			if(criteria2 != null) {
				criteria.put("C2", criteria2);
				metCriteria2 = true;
			}
			if(criteria3 != null) {
				criteria.put("C3", criteria3);
				metCriteria3 = true;		
			}
									
			//NOTE THAT for definite - assign the earliest index date of either probable or definite			
			//(definite/probable - earliest date that satisfies either PhD asthma or C2)
			
			//key=index date, val=toPrint
			TreeMap<String, String> definite = new TreeMap<String, String>();
			TreeMap<String, String> probable = new TreeMap<String, String>();

			//check if definite asthma 
			//1) physician diag AND any of two criteria (note that criteria1 is necessary for criteria2)
			//2) all three criteria
			if(isPhDiag && metCriteria1 && metCriteria2) {
				String inxDt = getIndexDate("PhD:C2",criteria,true);
				String val = mcn+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("PhD:C1:C2",criteria);
				definite.put(inxDt, val); 
			}
			if(isPhDiag && metCriteria1 && metCriteria3) {
				String inxDt = getIndexDate("PhD",criteria,true);
				String val = mcn+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("PhD:C1:C3",criteria); 
				definite.put(inxDt, val);
			}
			if(metCriteria1 && metCriteria2 && metCriteria3) {
				String inxDt = getIndexDate("C2",criteria,true);
				String val = mcn+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("C1:C2:C3",criteria); 
				definite.put(inxDt, val);
			}

			//check if probable asthma
			//1) physician diagnosis with one or none of criteria
			//2) only criteria1 and criteria2
			if(isPhDiag) {					
				String inxDt = getIndexDate("PhD",criteria,true);
				String val = mcn+DELIM+"PROBABLE"+DELIM+inxDt
						+DELIM+getEvidence("PhD", criteria); 
				probable.put(inxDt, val);
			}
			if(metCriteria1 && metCriteria2) {
				//C2 date is after C1
				String inxDt = getIndexDate("C2",criteria,true);
				String val = mcn+DELIM+"PROBABLE"+DELIM+inxDt
						+DELIM+getEvidence("C1:C2", criteria); 
				probable.put(inxDt, val);
			}

			//choose the earliest index date
			if(!definite.isEmpty()) {
				toPrint += definite.get(definite.firstKey())+"\n";				
			}				
			else if(!probable.isEmpty()) {
				toPrint += probable.get(probable.firstKey())+"\n";				
			}				
			//NO asthma
			else {
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
			}
			//----
		}

		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "asthma_pt.out";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam));
			bf.append(toPrint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	/*
	//print last follow-up date also
	public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException {
		super.collectionProcessComplete(arg0);
				
		String toPrint = "";
		for(String mcn : ptSumMap.keySet()) {
			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
			boolean isPhDiag = false;
			boolean metCriteria1 = false;
			boolean metCriteria2 = false;
			boolean metCriteria3 = false;
			String lfd = lastFuDate.get(mcn);
			
			if(dateInfoMap==null) { 
				//System.out.println(mcn + " has no concept");
				toPrint += mcn+DELIM+lfd+DELIM+"NO"+DELIM+DELIM+"\n";
				continue;
			}

			//check each criteria
			//Info is the earliest satisfied 
			List<Info> phDiag = getPhDiag(dateInfoMap);
			List<Info> criteria1 = getCriteria1(dateInfoMap);
			List<Info> criteria2 = getCriteria2(dateInfoMap);
			List<Info> criteria3 = getCriteria3(dateInfoMap);
			
			//key=criterion, val=list of Info
			Map<String, List<Info>> criteria = new HashMap<String, List<Info>>();
			
			if(phDiag != null) {
				criteria.put("PhD", phDiag);
				isPhDiag = true;
			}
			if(criteria1 != null) {
				criteria.put("C1", criteria1);
				metCriteria1 = true;
			}
			if(criteria2 != null) {
				criteria.put("C2", criteria2);
				metCriteria2 = true;
			}
			if(criteria3 != null) {
				criteria.put("C3", criteria3);
				metCriteria3 = true;		
			}
									
			//NOTE THAT for definite - assign the earliest index date of either probable or definite			
			//(definite/probable - earliest date that satisfies either PhD asthma or C2)
			
			//key=index date, val=toPrint
			TreeMap<String, String> definite = new TreeMap<String, String>();
			TreeMap<String, String> probable = new TreeMap<String, String>();

			//check if definite asthma 
			//1) physician diag AND any of two criteria (note that criteria1 is necessary for criteria2)
			//2) all three criteria
			if(isPhDiag && metCriteria1 && metCriteria2) {
				String inxDt = getIndexDate("PhD:C2",criteria,true);
				String val = mcn+DELIM+lfd+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("PhD:C1:C2",criteria);
				definite.put(inxDt, val); 
			}
			if(isPhDiag && metCriteria1 && metCriteria3) {
				String inxDt = getIndexDate("PhD",criteria,true);
				String val = mcn+DELIM+lfd+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("PhD:C1:C3",criteria); 
				definite.put(inxDt, val);
			}
			if(metCriteria1 && metCriteria2 && metCriteria3) {
				String inxDt = getIndexDate("C2",criteria,true);
				String val = mcn+DELIM+lfd+DELIM+"DEFINITE"+DELIM+inxDt
						+DELIM+getEvidence("C1:C2:C3",criteria); 
				definite.put(inxDt, val);
			}

			//check if probable asthma
			//1) physician diagnosis with one or none of criteria
			//2) only criteria1 and criteria2
			if(isPhDiag) {					
				String inxDt = getIndexDate("PhD",criteria,true);
				String val = mcn+DELIM+lfd+DELIM+"PROBABLE"+DELIM+inxDt
						+DELIM+getEvidence("PhD", criteria); 
				probable.put(inxDt, val);
			}
			if(metCriteria1 && metCriteria2) {
				//C2 date is after C1
				String inxDt = getIndexDate("C2",criteria,true);
				String val = mcn+DELIM+lfd+DELIM+"PROBABLE"+DELIM+inxDt
						+DELIM+getEvidence("C1:C2", criteria); 
				probable.put(inxDt, val);
			}

			//choose the earliest index date
			if(!definite.isEmpty()) {
				toPrint += definite.get(definite.firstKey())+"\n";				
			}				
			else if(!probable.isEmpty()) {
				toPrint += probable.get(probable.firstKey())+"\n";				
			}				
			//NO asthma
			else {
				toPrint += mcn+DELIM+lfd+DELIM+"NO"+DELIM+DELIM+"\n";
			}
			//----
		}

		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "asthma_pt.out";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam));
			bf.append(toPrint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	*/
}
