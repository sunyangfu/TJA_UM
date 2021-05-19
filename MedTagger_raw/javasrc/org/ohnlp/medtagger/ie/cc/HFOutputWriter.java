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
public class HFOutputWriter extends CasConsumer_ImplBase {
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
		exclSections = new HashSet<String>();
		noMCN = new HashSet<String>();
		/*
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
		*/
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
				if(!cm.getCertainty().equals("Positive")) {
					boolean isFound = cm.getSentence().getCoveredText().indexOf("No.") !=-1? true: false;
					if (!isFound) continue;
				}
				if(cm.getStatus().equals("FAMILY HISTORY")) continue; //use Present and HistoryOf
				if(cm.getStatus().equals("HistoryOf")) continue;
				if(!cm.getExperiencer().equals("Patient")) continue; 
		
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
				
	
	/**
	 * 
	 * @param date1 (yyyyMMdd)
	 * @param date2 (yyyyMMdd)
	 * @return difference in days (date2 - date1)
	 */
	
	
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
		
					
		String toPrint = "";
		//For each patient iterate all notes
		for(String mcn : ptSumMap.keySet()) {
			TreeMap<String, ArrayList<Info>> dateInfoMap = ptSumMap.get(mcn);
			boolean isHF = false;
			
			
			//mcn|category|index_date|evidence
			
			if(dateInfoMap==null) { 
				//System.out.println(mcn + " has no concept");
				toPrint += mcn+DELIM+""+DELIM+DELIM+"\n";
				continue;
			}

			//process per date - assume one operation per day
			for(String date : dateInfoMap.keySet()) {				
				String sent = "";
				ArrayList<Info> Infos = dateInfoMap.get(date);
				for(Info i : Infos) {
					//Direct mention		
					if(i.norm.equals("HF")) {
						if(!i.senId.contains("FAMILY HISTORY")){
							isHF = true;
							sent += "<hf>" + "<"+ i.senId + ">" + i.sentence+"~";	
						}
					}	
					
				}
				
				String fxt = "";
				if(isHF) fxt = "hf";
				else { 
						System.out.println("NO HF:" 
								+ mcn + DELIM + date);
					}
				
				
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
