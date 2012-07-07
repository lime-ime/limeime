package net.toload.main.hd.global;

public class KeyboardObj {

	private String code;
	private String name;
	private String description;
	private String type;
	private String image;
	private String imkb;
	private String imshiftkb;
	private String engkb;
	private String engshiftkb;
	private String symbolkb;
	private String symbolshiftkb;
	private String defaultkb;
	private String defaultshiftkb;
	private String extendedkb;
	private String extendedshiftkb;
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	public String getImkb() {
		return imkb;
	}
	public void setImkb(String imkb) {
		this.imkb = imkb;
	}
	public String getImshiftkb() {
		return imshiftkb;
	}
	public void setImshiftkb(String imshiftkb) {
		this.imshiftkb = imshiftkb;
	}
	public String getEngkb() {
		return engkb;
	}
	public String getEngkb(boolean showNumberRow) {
		if(showNumberRow) return "lime_english_number";
		else return "lime_english";
	}
	public void setEngkb(String engkb) {
		this.engkb = engkb;
	}
	public String getEngshiftkb() {
		return engshiftkb;
	}
	public String getEngshiftkb(boolean showNumberRow) {
		if(showNumberRow) return "lime_english_number_shift";
		else return "lime_english_shift";
	}
	public void setEngshiftkb(String engshiftkb) {
		this.engshiftkb = engshiftkb;
	}
	public String getSymbolkb() {
		return symbolkb;
	}
	public void setSymbolkb(String symbolkb) {
		this.symbolkb = symbolkb;
	}
	public String getSymbolshiftkb() {
		return symbolshiftkb;
	}
	public void setSymbolshiftkb(String symbolshiftkb) {
		this.symbolshiftkb = symbolshiftkb;
	}
	public String getDefaultkb() {
		return defaultkb;
	}
	public void setDefaultkb(String defaultkb) {
		this.defaultkb = defaultkb;
	}
	public String getDefaultshiftkb() {
		return defaultshiftkb;
	}
	public void setDefaultshiftkb(String defaultshiftkb) {
		this.defaultshiftkb = defaultshiftkb;
	}
	public String getExtendedkb() {
		return extendedkb;
	}
	public void setExtendedkb(String extendedkb) {
		this.extendedkb = extendedkb;
	}
	public String getExtendedshiftkb() {
		return extendedshiftkb;
	}
	public void setExtendedshiftkb(String extendedshiftkb) {
		this.extendedshiftkb = extendedshiftkb;
	}
	
	
}
