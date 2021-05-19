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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.ohnlp.medtagger.ie.type.Match;
import org.ohnlp.medtagger.type.ConceptMention;
import org.ohnlp.typesystem.type.structured.Document;
import org.ohnlp.typesystem.type.syntax.NewlineToken;
import org.ohnlp.typesystem.type.textspan.Segment;
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
public class AsthmaAPIPtLevelOutputWriter extends CasConsumer_ImplBase {
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
	private TreeMap<String, TreeMap<String, ArrayList<Info>>> ptSumMap; //TreeMap is for sorted key
	private Map<String, String> ppiParentsAsthma; //key=mcn, val=farther/mother|date of answer(yyyy-mm-dd) 
	private Map<String, String> eosinophils; //key=mcn, val=valueInPercent|labTestDate(yyyy-mm-dd)

	//private Set<String> diagSections; 
	private Set<String> exclSections; 
	//private Set<String> parentsAsthmaSections; 
	private Map<String, String> lastFuDate; //key=mcn (assume unique mcn), val=date (yyyymmdd)
	private Set<String> noMCN; 
	
	public void initialize() throws ResourceInitializationException {
		mDocNum = 0;
		mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
		if (!mOutputDir.exists()) {
			mOutputDir.mkdirs();
		} 
		
		ptSumMap = new TreeMap<String, TreeMap<String, ArrayList<Info>>>();
		/*
		diagSections = new HashSet<String>();
		diagSections.add("20113");
		
		parentsAsthmaSections = new HashSet<String>();
		parentsAsthmaSections.add("20109"); //family history
		parentsAsthmaSections.add("20103"); //history of present illness
		//parentsAsthmaSections.add("20112");
		*/
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
			//clinic\tlastfudate(yyyymmdd)\tstatus
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
			//docName: 05494407_76414819_2_MIS_Ped-Comm-NW_2004-07-27.txt
			//NOTE docName format is different from PAC
			mcn = docName.split("_")[0]; //8 digits
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
		
		List<Info> infoInDoc = new ArrayList<Info>();
		//TODO no restrict parents' asthma based on date??
		if(!skip) { 
			//System.out.println("  "+mcn+"|"+lstDate+"|"+noteDate);			
			FSIterator<? extends Annotation> cmIter = jcas.getAnnotationIndex(
					ConceptMention.type).iterator();
			while (cmIter.hasNext()) {
				ConceptMention cm = (ConceptMention) cmIter.next();

				//skip exclusion sections
				if(!cm.getNormTarget().equals("PARENTS_ASTHMA")) {
					if(exclSections.contains(cm.getSentence().getSegment().getId())) continue;
				}
				
				//check assertion
				if(!cm.getCertainty().equals("Positive")) continue;
				if(cm.getStatus().equals("FamilyHistoryOf")) continue; //use Present and HistoryOf
				if( cm.getStatus().equals("HistoryOf") 
						&& (!cm.getNormTarget().equals("WHEEZE")) ) continue; //allow HistoryOf only for WHEEZE
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

			//printAnnotations(infoInDoc); //moved to the end
		}
		
		//parents' asthma
		//ad-hoc rule: "mother\nasthma" in 20109
		FSIterator<? extends Annotation> segIter = jcas.getAnnotationIndex(
				Segment.type).iterator();	
		while (segIter.hasNext()) {
			Segment seg = (Segment) segIter.next();
			if(seg.getId().equals("20109")) {
				Pattern p = Pattern.compile("(mother|father|mom|dad)(\\s+)?(-)?(\\s+)?\nasthma",
						Pattern.CASE_INSENSITIVE);
				Matcher m = p.matcher(seg.getCoveredText());
				if(m.find()) {
					Info i = new Info(noteDate, docName, m.group().replaceAll("\\s+", " "), "PARENTS_ASTHMA",
							m.start(), m.end(), m.group().replaceAll("\\s+", " "),
							"null", seg.getId());
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
			}
		}
		
		printAnnotations(infoInDoc);
	}
	
	
	/**
	 * Return Info if exist two or more of wheezing and ( (cough|dyspnea) or wheezing in LE) for the same date, 
	 * OW return null
	 * The episodes should be at least 3 weeks apart within 1 years
	 * @param dateInfo
	 * @return
	 */
	/*
	private ArrayList<Info> getFrequentWheezer(TreeMap<String, ArrayList<Info>> dateInfo) {		
		ArrayList<Info> ret = new ArrayList<Info>();
		List<String> metCondDate = new ArrayList<String>();
		//For the same date
		for(String date : dateInfo.keySet()) {
			boolean hasWheeze = false;	
			boolean hasWheezeInLE=false;
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("WHEEZE")) { 
					hasWheeze = true;
					if(i.docName.split("_")[3].equals("LE")) hasWheezeInLE=true;
					ret.add(i);
				}
			}
			
			boolean hasCoughOrDyspnea=false;
			if(hasWheeze) { 
				for(Info i : dateInfo.get(date)) {
					if(i.norm.equals("COUGH")
							|| i.norm.equals("DYSPNEA")) { 
						hasCoughOrDyspnea = true;
						metCondDate.add(date);
						ret.add(i);
					}
				}
			}
			
			//TODO check
			if( (!hasCoughOrDyspnea) && hasWheezeInLE ) metCondDate.add(date);
				
			//check if occurs 2 times or more (at least 3 weeks apart within "1" years)
			for(int i=0; i<metCondDate.size(); i++) {
				for(int j=i+1; j<metCondDate.size(); j++) { 
					int interval = getDateDiff(metCondDate.get(i), metCondDate.get(j));
					if(interval<=21) continue; 
					if(interval>365) break; //within 1 year
					
					return ret;
				}
			}
		}
		
		return null;
	}
	*/
	
	/**
	 * Return Info if exist two or more of wheezing for the same date, OW return null
	 * The episodes should be at least 3 weeks apart within 1 year
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getFrequentWheezer(TreeMap<String, ArrayList<Info>> dateInfo) {		
		ArrayList<Info> ret = new ArrayList<Info>();
		List<String> metCondDate = new ArrayList<String>();
		//For the same date
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("WHEEZE")) { 
					metCondDate.add(date);
					ret.add(i);
				}
			}
			
			//check if occurs 2 times or more (at least 3 weeks apart within "1" years)
			for(int i=0; i<metCondDate.size(); i++) {
				for(int j=i+1; j<metCondDate.size(); j++) { 
					int interval = getDateDiff(metCondDate.get(i), metCondDate.get(j));
					if(interval<=21) continue; 
					//if(interval>365) break; //within 1 year
					if(interval>1095) break; //within 3 year

					return ret;
				}
			}
		}
		
		return null;
	}
	/**
	 * Return Info if exists parents asthma from either clinical notes or PPI, OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getParentsAsthma(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("PARENTS_ASTHMA") || i.norm.equals("PPI_PARENTS_ASTHMA")) {
					ret.add(i);
					return ret;
				}				
			}
		}
		
		return null;
	}
		
	/**
	 * Return the Info if exists eczema OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getPhDiagEczema(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				//if(!diagSections.contains(i.section)) continue;				
				if(i.norm.equals("INFANTILE_ECZEMA")) {
					ret.add(i);
					return ret;
				}				
			}
		}
		
		return null;
	}
	
	/**
	 * Return the Info if there exists allergic rhinitis/hay fever OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getPhDiagAllergicRhinitis(TreeMap<String, ArrayList<Info>> dateInfo) {		
		ArrayList<Info> ret = new ArrayList<Info>();
		//For the same date	
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				//if(!diagSections.contains(i.section)) continue;				
				if(i.norm.equals("EXPOSURE_TO_ANTIGEN") || i.norm.equals("HAY_FEVER")) {
					ret.add(i);
					return ret;
				}				
			}
		}
		
		return null;
	}
	
	/**
	 * Return the Info if there exists wheezing apart from cold, OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getWheezingApartFromCold(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		//For the same date
		for(String date : dateInfo.keySet()) {
			boolean hasWheeze = false;			
			for(Info i : dateInfo.get(date)) {
				if(i.norm.equals("WHEEZE")) { 
					hasWheeze = true;
					ret.add(i);
				}
			}
			
			boolean metCond = false;
			if(hasWheeze) { 
				for(Info i : dateInfo.get(date)) {
					if(i.norm.equals("COUGH")
							|| i.norm.equals("DYSPNEA")) { 
						ret.add(i);
						metCond = true;
					}
				}
			}
			
			if(metCond) {
				boolean hasCold = false;
				for(Info i : dateInfo.get(date)) {
					if(i.norm.equals("COLD")
							|| i.norm.equals("COLD_SYMPTOM")) { 
						hasCold = true;
						break;
					}
					if(i.norm.startsWith("TEMPERATURE")) {
						double temperature = Double.parseDouble(i.norm.split("-")[1]);
						if(temperature >= 100.4) {
							hasCold = true;
							break;
						}
					}
				}
				if(!hasCold) return ret;
			}
		}

		return null;
	}
		
	/**
	 * Return Info if exists high Eosinophils, OW return null
	 * @param dateInfo
	 * @return
	 */
	private ArrayList<Info> getHighEosinophils(TreeMap<String, ArrayList<Info>> dateInfo) {
		ArrayList<Info> ret = new ArrayList<Info>();
		//For the same date	
		for(String date : dateInfo.keySet()) {
			for(Info i : dateInfo.get(date)) {
				if(i.norm.startsWith("EOSINOPHILS_PERCENT")) {
					double val = Double.parseDouble(i.norm.split("-")[1]);
					if(val >= 4.0) {
						ret.add(i);
						return ret;
					}
				}				
			}
		}
			
		return null;
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
							+ File.separator + "asthma_API.out";
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
	
	private String getIndexDate(String criteriaMet, Map<String, List<Info>> criteria) {
		SortedSet<String> date = new TreeSet<String>();
		
		String[] crit = criteriaMet.split(":");
		for(int i=0; i<crit.length; i++) {
			List<Info> info = criteria.get(crit[i]);
			if(info==null) {
				System.out.println(crit[i] + " has NO Info");
				System.exit(1);
			}
			//date is in order of old to recent 
			date.add(info.get(info.size()-1).date);
		}
		
		return date.last();
	}
		
	/**
	 * This method calls once after completion of the process of all documents
	 * Perform a pt-level asthma status based on the collection of doc-level information in ptSumMap
	 */
	public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException {
		super.collectionProcessComplete(arg0);
		
		String toPrint = "";
		for(String mcn : ptSumMap.keySet()) {
			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
			boolean isFreqWheezer = false;
			boolean hasParentsAsthma = false; //From PPI & Familiy History in CN
			boolean isPhDiagEczema = false;
			boolean isPhDAllergicRhinitis = false;
			boolean hasWheezingApartFromCold = false;
			boolean hasHighEosiniphils = false; //From Lab

			if(dateInfoMap==null) { 
				//System.out.println(mcn + " has no concept");
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
				continue;
			}
			
			//check each criteria
			List<Info> FreqWheezer = getFrequentWheezer(dateInfoMap);
			List<Info> parentsAsthma = getParentsAsthma(dateInfoMap);
			List<Info> phDiagEczema = getPhDiagEczema(dateInfoMap);
			List<Info> phDAllergicRhinitis = getPhDiagAllergicRhinitis(dateInfoMap);
			List<Info> wheezingAaprtFromCold = getWheezingApartFromCold(dateInfoMap);
			List<Info> highEosiniphils = getHighEosinophils(dateInfoMap);
			
			Map<String, List<Info>> criteria = new HashMap<String, List<Info>>();
			
			if(FreqWheezer != null) {
				criteria.put("FreqWheezer", FreqWheezer);
				isFreqWheezer = true;
			}
			if(parentsAsthma != null) {
				criteria.put("ParentsAsthma", parentsAsthma);
				hasParentsAsthma = true;
			}
			if(phDiagEczema != null) {
				criteria.put("Eczema", phDiagEczema);
				isPhDiagEczema = true;
			}
			if(phDAllergicRhinitis != null) {
				criteria.put("AllergicRhinitis", phDAllergicRhinitis);
				isPhDAllergicRhinitis = true;
			}
			if(wheezingAaprtFromCold != null) {
				criteria.put("WheezingNoCold", wheezingAaprtFromCold);
				hasWheezingApartFromCold = true;
			}
			if(highEosiniphils != null) {
				criteria.put("HighEosiniphils", highEosiniphils);
				hasHighEosiniphils = true;
			}
			
			/*
			//May not get the earliest index date among all possible criteria 
			//DO NOT change the order 		
			//prerequisite
			if(!isFreqWheezer) {
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
			}
			//at least one of majors
			else if(hasParentsAsthma) {
				toPrint += mcn+DELIM+"YES"+DELIM+getIndexDate("FreqWheezer:ParentsAsthma",criteria)
							+DELIM+getEvidence("FreqWheezer:ParentsAsthma",criteria)+"\n";						
			}
			else if(isPhDiagEczema) {
				toPrint += mcn+DELIM+"YES"+DELIM+getIndexDate("FreqWheezer:Eczema",criteria)
							+DELIM+getEvidence("FreqWheezer:Eczema",criteria)+"\n";						
			}
			//at least two of minors
			else if(isPhDAllergicRhinitis && hasWheezingApartFromCold) {
				toPrint += mcn+DELIM+"YES"+DELIM+getIndexDate("FreqWheezer:AllergicRhinitis:WheezingNoCold",criteria)
							+DELIM+getEvidence("FreqWheezer:AllergicRhinitis:WheezingNoCold",criteria)+"\n";						
			}
			else if(isPhDAllergicRhinitis && hasHighEosiniphils) {
				toPrint += mcn+DELIM+"YES"+DELIM+getIndexDate("FreqWheezer:AllergicRhinitis:HighEosiniphils",criteria)
							+DELIM+getEvidence("FreqWheezer:AllergicRhinitis:HighEosiniphils",criteria)+"\n";						
			}
			else if(hasWheezingApartFromCold && hasHighEosiniphils) {
				toPrint += mcn+DELIM+"YES"+DELIM+getIndexDate("FreqWheezer:WheezingNoCold:HighEosiniphils",criteria)
							+DELIM+getEvidence("FreqWheezer:WheezingNoCold:HighEosiniphils",criteria)+"\n";						
			}
			//no condition met
			else {
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
			}
			*/
			
			//prerequisite
			if(!isFreqWheezer) {
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
				continue;
			}
			
			//key=index date, val=toPrint
			TreeMap<String, String> api = new TreeMap<String, String>();
			
			//at least one of majors
			if(hasParentsAsthma) {
				String inxDt = getIndexDate("FreqWheezer:ParentsAsthma",criteria);
				String val = mcn+DELIM+"YES"+DELIM+inxDt
							+DELIM+getEvidence("FreqWheezer:ParentsAsthma",criteria);
				api.put(inxDt, val); 				
			}
			
			if(isPhDiagEczema) {
				String inxDt = getIndexDate("FreqWheezer:Eczema",criteria);
				String val = mcn+DELIM+"YES"+DELIM+inxDt
							+DELIM+getEvidence("FreqWheezer:Eczema",criteria);
				api.put(inxDt, val); 						
			}
			
			//at least two of minors
			if(isPhDAllergicRhinitis && hasWheezingApartFromCold) {
				String inxDt = getIndexDate("FreqWheezer:AllergicRhinitis:WheezingNoCold",criteria);
				String val = mcn+DELIM+"YES"+DELIM+inxDt
							+DELIM+getEvidence("FreqWheezer:AllergicRhinitis:WheezingNoCold",criteria);
				api.put(inxDt, val); 						
			}
			
			if(isPhDAllergicRhinitis && hasHighEosiniphils) {
				String inxDt = getIndexDate("FreqWheezer:AllergicRhinitis:HighEosiniphils",criteria);
				String val = mcn+DELIM+"YES"+DELIM+inxDt
							+DELIM+getEvidence("FreqWheezer:AllergicRhinitis:HighEosiniphils",criteria);
				api.put(inxDt, val); 					
			}
			
			if(hasWheezingApartFromCold && hasHighEosiniphils) {
				String inxDt = getIndexDate("FreqWheezer:WheezingNoCold:HighEosiniphils",criteria);
				String val = mcn+DELIM+"YES"+DELIM+inxDt
							+DELIM+getEvidence("FreqWheezer:WheezingNoCold:HighEosiniphils",criteria);
				api.put(inxDt, val); 					
			}
			
			if(!api.isEmpty()) {
				toPrint += api.get(api.firstKey())+"\n";				
			}	
			//no condition met
			else {
				toPrint += mcn+DELIM+"NO"+DELIM+DELIM+"\n";
			}
			
		}
		
		try {
			String fnam = (String) getConfigParameterValue(PARAM_OUTPUTDIR) 
					+ File.separator + "asthma_API_pt.out";
			BufferedWriter bf = new BufferedWriter(new FileWriter(fnam));
			bf.append(toPrint);
			bf.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}
