package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

@Table
public class Stars {
    @Id
    public int id;
    public String displayedAs; // e.g. "•", "••", "•••", "••••", "•••••"
    public int value;           // numeric value 1-5

    /** Populated only when the query includes {@code JOIN(Rating.class)}; otherwise null. */
    @OneToMany public List<Rating> ratings;
}
