package org.opentides.example.bean;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.opentides.annotation.Auditable;
import org.opentides.annotation.PrimaryField;
import org.opentides.annotation.field.*;
import org.opentides.bean.*;
import org.opentides.util.StringUtil;
import org.opentides.web.json.Views;
import org.opentides.web.json.serializer.TagsSerializer;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.*;
import javax.persistence.JoinTable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

//import org.opentides.annotation.Secure;

/**
 * This is the master class for testing all annotations
 * and code generation supported by opentides3.
 */ 
@Entity  
@Table(name="NINJA")
@Auditable(excludeFields = {"tags"})
public class Ninja extends BaseEntity implements Commentable, ImageUploadable, Taggable {
	
	private static final long serialVersionUID = -4142599915292096152L;
	
	// Label: specified
	// Validation: required
	// Use: crud, search criteria
	@Validation(isRequired = true, maxLength = 128)
	@Column(name = "FIRST_NAME", nullable=false)
	@JsonView(Views.FormView.class)
	@TextField(label="Name", isSearchCriteria=true)
	private String firstName;

	// TextField
	// Label: default
	// Validation: required
	// Use: crud, search criteria
	@Validation(isRequired = true)
	@TextField(isSearchCriteria=true)
	@Column(name = "LAST_NAME", nullable=false)
	@JsonView(Views.FormView.class)
	private String lastName;

	// TextField
	// Label: specified
	// Validation: email, required
	// Use: crud, search criteria, search results
	@Validation(isRequired = true, isEmailFormat = true)
	@TextField(label="Email Address", isSearchCriteria=true, isSearchResult=true)
	@Column(name="EMAIL", nullable=false)	
	@JsonView(Views.SearchView.class)
	private String email;

	// TextArea
	// Label: default
	// Validation: Must be protected from script/html injection
	// Use: crud
	// Store as blob
	@TextArea
	private String description;
	
	@Validation(minAllowValue = 17, maxAllowValue = 65)
	@JsonView(Views.SearchView.class)
	private Integer age;
	
	// Display only
	// Label: specified
	// Use: read-only (score is system generated)
	@DisplayOnly
	@JsonView(Views.SearchView.class)
	private Long score;
	
	// Date Picker
	// Label: specified
	// Validation: today or past date
	// Use: crud
	@Validation(isRequired = true, rejectPastDate = true)
	@DatePicker
	@Temporal(TemporalType.TIMESTAMP)
	@JsonView(Views.SearchView.class)
	private Date joinDate;
	
	// Label: specified
	// Validation: none
	// Use: crud
	// Secured to authz only
	@Checkbox
	//@Secure
	@JsonView(Views.SearchView.class)
	private Boolean active;
	
	// 
	private transient Clan mainClan;
	
	private transient Set<Clan> subClans;
	
	// Dropdown
	// Validation: required
	// Default to STATUS_NEW	
	// Secured to authz only
	@JsonView(Views.FormView.class)
	@JoinColumn(name = "STATUS_ID")	
	private SystemCodes status;

	// Hidden
	// Validation: only himself can view/edit
	// Use: secret
	@JsonView(Views.FormView.class)
	private String secretCode;
	
	// Dropdown (list of all ninjago)
	// Label: default
	// Validation: must not be self
	// Use: 
	private Ninja partner;
	
	// Multiselect
	// Label: Specified
	// Validation: none
	@JsonView(Views.FormView.class)
	@ManyToMany(fetch=FetchType.LAZY, cascade={CascadeType.MERGE, CascadeType.REFRESH})
	@JoinTable(name="NINJA_SKILLS",
	joinColumns = { 
			@JoinColumn(name="NINJA_ID", referencedColumnName="ID") 
	},
	inverseJoinColumns = {
			@JoinColumn(name="SKILLS_ID")
	})
	private Set<SystemCodes> skillSet;
	
	// Radiobutton
	// Searchable
	@Validation(isRequired = true)
	@JsonView(Views.FormView.class)
	private String gender;
	
	// Future date
	@Temporal(TemporalType.TIMESTAMP)	
	private Date nextFight;
	
	// Currency
	private Double sellingPrice;
	
	// image/photo
	private String avatar;
	
	// file upload
	private String attachment;
	
	@OneToMany(cascade = CascadeType.REMOVE)
	@JoinTable(name="NINJA_TAGS",
	joinColumns = { 
			@JoinColumn(name="NINJA_ID", referencedColumnName="ID") 
	},
	inverseJoinColumns = {
			@JoinColumn(name="TAG_ID")
	})
	@JsonView(Views.FormView.class)
	private List<Tag> tags;

	/**
	 * @return the firstName
	 */
	public final String getFirstName() {
		return firstName;
	}

	/**
	 * @param firstName the firstName to set
	 */
	public final void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	/**
	 * @return the lastName
	 */
	public final String getLastName() {
		return lastName;
	}

	/**
	 * @param lastName the lastName to set
	 */
	public final void setLastName(String lastName) {
		this.lastName = lastName;
	}

	/**
	 * @return the email
	 */
	public final String getEmail() {
		return email;
	}

	/**
	 * @param email the email to set
	 */
	public final void setEmail(String email) {
		this.email = email;
	}

	/**
	 * @return the description
	 */
	public final String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public final void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the age
	 */
	public final Integer getAge() {
		return age;
	}

	/**
	 * @param age the age to set
	 */
	public final void setAge(Integer age) {
		this.age = age;
	}

	/**
	 * @return the score
	 */
	public final Long getScore() {
		return score;
	}

	/**
	 * @param score the score to set
	 */
	public final void setScore(Long score) {
		this.score = score;
	}

	/**
	 * @return the joinDate
	 */
	public final Date getJoinDate() {
		return joinDate;
	}

	/**
	 * @param joinDate the joinDate to set
	 */
	public final void setJoinDate(Date joinDate) {
		this.joinDate = joinDate;
	}

	/**
	 * @return the active
	 */
	public final Boolean getActive() {
		return active;
	}

	/**
	 * @param active the active to set
	 */
	public final void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * @return the mainClan
	 */
	public final Clan getMainClan() {
		return mainClan;
	}

	/**
	 * @param mainClan the mainClan to set
	 */
	public final void setMainClan(Clan mainClan) {
		this.mainClan = mainClan;
	}

	/**
	 * @return the subClans
	 */
	public final Set<Clan> getSubClans() {
		return subClans;
	}

	/**
	 * @param subClans the subClans to set
	 */
	public final void setSubClans(Set<Clan> subClans) {
		this.subClans = subClans;
	}

	/**
	 * @return the status
	 */
	public final SystemCodes getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 */
	public final void setStatus(SystemCodes status) {
		this.status = status;
	}

	/**
	 * @return the secretCode
	 */
	public final String getSecretCode() {
		return secretCode;
	}

	/**
	 * @param secretCode the secretCode to set
	 */
	public final void setSecretCode(String secretCode) {
		this.secretCode = secretCode;
	}

	/**
	 * @return the partner
	 */
	public final Ninja getPartner() {
		return partner;
	}

	/**
	 * @param partner the partner to set
	 */
	public final void setPartner(Ninja partner) {
		this.partner = partner;
	}

	/**
	 * @return the skillSet
	 */
	public final Set<SystemCodes> getSkillSet() {
		return skillSet;
	}

	/**
	 * @param skillSet the skillSet to set
	 */
	public final void setSkillSet(Set<SystemCodes> skillSet) {
		this.skillSet = skillSet;
	}

	/**
	 * @return the gender
	 */
	public final String getGender() {
		return gender;
	}

	/**
	 * @param gender the gender to set
	 */
	public final void setGender(String gender) {
		this.gender = gender;
	}

	/**
	 * @return the nextFight
	 */
	public final Date getNextFight() {
		return nextFight;
	}

	/**
	 * @param nextFight the nextFight to set
	 */
	public final void setNextFight(Date nextFight) {
		this.nextFight = nextFight;
	}

	/**
	 * @return the sellingPrice
	 */
	public final Double getSellingPrice() {
		return sellingPrice;
	}

	/**
	 * @param sellingPrice the sellingPrice to set
	 */
	public final void setSellingPrice(Double sellingPrice) {
		this.sellingPrice = sellingPrice;
	}

	/**
	 * @return the avatar
	 */
	public final String getAvatar() {
		return avatar;
	}

	/**
	 * @param avatar the avatar to set
	 */
	public final void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	/**
	 * @return the attachment
	 */
	public final String getAttachment() {
		return attachment;
	}

	/**
	 * @param attachment the attachment to set
	 */
	public final void setAttachment(String attachment) {
		this.attachment = attachment;
	}
	
	@JsonView(Views.SearchView.class)
	@PrimaryField(label="Name")
	public final String getCompleteName() {
		String name = "";
		if (!StringUtil.isEmpty(getFirstName())) {
			name += getFirstName() + " ";
		}
		if (!StringUtil.isEmpty(getLastName())) {
			name += getLastName() + " ";
		}
		return name.trim();
	}
	
	// ImageUploadable requirements
	@OneToMany(cascade=CascadeType.ALL, fetch = FetchType.LAZY)
	@JoinTable(name = "NINJA_PHOTO", 
			joinColumns = { @JoinColumn(name = "NINJA_ID", referencedColumnName = "ID")}, 
			inverseJoinColumns = {
						@JoinColumn(name = "PHOTO_ID")}
	)
	private List<ImageInfo> images;
	
	private transient MultipartFile image;
	
	@Override
	public List<ImageInfo> getImages() {
		return images;
	}
	
	@Override
	public ImageInfo getPrimaryImage() {
		if(!CollectionUtils.isEmpty(this.images)) {
			for(ImageInfo imageInfo : this.images) {
				if(imageInfo.getIsPrimary()) {
					return imageInfo;
				}
			}
		}
		ImageInfo primaryPhoto = new ImageInfo();
		primaryPhoto.setId(0l);
		return primaryPhoto;
	}
	
	@Override
	public MultipartFile getImage() {
		return image;
	}
	
	public void setImage(MultipartFile image) {
		this.image = image;
	}
	
	public void setImages(List<ImageInfo> images) {
		this.images = images;
	}
	
	@Override
	public void addImage(ImageInfo imageInfo){
		synchronized (imageInfo) {
			if (images == null){
				images = new ArrayList<ImageInfo>();
			}
			images.add(imageInfo);
		}
	}
	
	// End of ImageUploadable requirements
	
	// Commentable requirements
	
	@OneToMany(cascade=CascadeType.ALL, fetch = FetchType.LAZY)
	@JoinTable(name = "NINJA_COMMENT", 
			joinColumns = { @JoinColumn(name = "NINJA_ID", referencedColumnName = "ID") }, 
			inverseJoinColumns = @JoinColumn(name = "COMMENT_ID")
	)
	private List<Comment> comments;

	@Override
	public List<Comment> getComments() {
		return comments;
	}
	
	@Override
	public void setComments(List<Comment> comments) {
		this.comments = comments;
	}
	
	@JsonSerialize(using = TagsSerializer.class)
	@Override
	public List<Tag> getTags() {
		return tags;
	}
	
	@Override
	public void setTags(List<Tag> tags) {
		this.tags = tags;
	}
	
}