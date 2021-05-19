package org.ohnlp.medtagger.ie.cc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PADPatientLevelOutput {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Map<String, Set<String>> clsMap = new TreeMap<String, Set<String>>(); 
		Map<String, List<String>> evidenceMap = new HashMap<String, List<String>>(); 

		try {
			//String fnam="/Volumes/bmi/Dept/Sohn/Projects/PAD_Olson/Output/output_TestNoVascLab_12172014.txt";
			String fnam="/Volumes/bmi/Dept/projects/Text/PAD_Olson/Output/nlp_09APR15_gold_all_20150515.txt";
			BufferedReader br = new BufferedReader(new FileReader(fnam));
			
			String line="";
			while( (line=br.readLine())!=null ) {
				if(line.length()==0) continue;
				
				String[] f = line.split("\\|");
				//String[] f = line.split("\t");
				String mcn = f[0].split("_")[0];
				String cls = f[1];
				
				Set<String> clsSet = clsMap.get(mcn);
				if(clsSet==null) clsSet = new HashSet<String>();
				clsSet.add(cls);
				clsMap.put(mcn, clsSet);
				
				if(f.length>2) {
					String evidence = line;
					List<String> evidenceList = evidenceMap.get(mcn);
					if(evidenceList==null) evidenceList = new ArrayList<String>();
					evidenceList.add(evidence);
					evidenceMap.put(mcn, evidenceList);	
				}
			}
			br.close();		
		} catch (IOException e1) {
			e1.printStackTrace();
		}	
		
		for(String mcn : clsMap.keySet()) {
			String cls = "CONTROL";
			Boolean def = false;
			Boolean pro = false;
			for(String c : clsMap.get(mcn)) {
				if(c.equals("DEFINITE")) {
					cls = c;
					def = true;
					break;
				}
				else if(!def && c.equals("PROBABLE")) {
					cls = c;
					pro = true;
				}
				else if(!def && !pro && c.equals("POSSIBLE")) {
					cls = c;
				}					
			}
			
//			String evidence="";
//			if(!cls.equals("CONTROL")) {
//				for(String s : evidenceMap.get(mcn)) {
//					if(evidence.equals(""))
//						evidence += s;
//					else 
//						evidence += "`"+s;
//				}
//			}
//			System.out.println(mcn+"||"+cls+"||"+evidence);
			
			System.out.println(mcn+"|"+cls);
		}		
	}

}
