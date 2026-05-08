package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

@Table
public class Artwork {
    @Id
    public int id;
    public String displayedAs;
    public String material;
    public String pictureUrl = "";
    public Artist artist;
    public Provenance provenance;

    /** Populated only when the query includes {@code JOIN(Rating.class)}; otherwise null. */
    @OneToMany public List<Rating> ratings;
}
