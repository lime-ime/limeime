package net.toload.main;

public class MappingIdx {

	private String code;
	private String next;
	private int size;
	
	MappingIdx(String code, String next, String size){
		this.setCode(code);
		this.setNext(next);
		this.setSize(size);
	}

	MappingIdx(String code, String next, int size){
		this.setCode(code);
		this.setNext(next);
		this.setSize(size);
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
	/**
	 * @return the next
	 */
	public String getNext() {
		return next;
	}
	/**
	 * @param next the next to set
	 */
	public void setNext(String next) {
		this.next = next;
	}
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
	
	public void setSize(String size){
		this.size = Integer.parseInt(size);
	}
	
}
