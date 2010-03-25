package net.toload.main;

import java.util.LinkedList;
import java.util.List;

import android.util.Log;

public class MappingSet {

	private List<Mapping> list;
	private String code;
	private int size;
	
	
	/**
	 * @return the size
	 */
	public int getSize() {
		return size;
	}

	/**
	 * @param size the size to set
	 */
	public void setSize(int size) {
		this.size = size;
	}


	MappingSet(){
		list = new LinkedList();
	}

	MappingSet(List list, int size){
		this.list = list;
		this.size = size;
	}
	
	/**
	 * @return the list
	 */
	public List getList() {
		return list;
	}

	public void removeMapping(Mapping unit){
		if(unit != null && list != null){
			for(int i=0; i < list.size(); i++){
				Mapping temp = list.get(i);
				if(temp.getCode().equalsIgnoreCase(unit.getCode()) && 
						temp.getWord().equals(unit.getWord()) ){
					list.remove(i);
					break;
				}
			}
		}
	}
	
	public void updateMapping(Mapping unit){
		if(unit != null && list != null){
			unit.setScore(unit.getScore() + 1);
			
			if(list.size() >0 && list.size() == 1){
				list.clear();
				list.add(unit);
			}else if(list.size() > 1){
				removeMapping(unit);
				addMapping(unit);
			}
			
		}
	}
	
	public void addMapping(Mapping unit){
		if(unit != null && list != null){
			int tempscore = unit.getScore();
			if(tempscore != 0){
				int count = 0;
				if(list.size() > 0){
					for(Mapping temp : list){
						if(unit.getScore() > temp.getScore()){
							list.add(count, unit);
						}
						count++;
					}
				}else{
					list.add(unit);
				}
			}else{
				list.add(unit);
			}
		}
	}
	
	/**
	 * @param list the list to set
	 */
	public void setList(List list) {
		this.list = list;
	}

	/**
	 * @return the code
	 */
	public String getCode() {
		return code;
	}

	/**
	 * @param code the code to set
	 */
	public void setCode(String code) {
		this.code = code;
	}
	
	
}
