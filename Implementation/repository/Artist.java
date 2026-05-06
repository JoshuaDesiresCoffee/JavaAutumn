package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class Artist {
    @Id
    public int id;
    public String displayedAs;
    public String fullName;
    public String birthDate;
    public String deathDate;
    public String bioUrl;
    public String pictureUrl;
}
