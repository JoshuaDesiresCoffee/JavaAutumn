package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

@Table
public class Epoch {
    @Id
    public int id;
    public String displayedAs;

    /** Populated only when the query includes {@code JOIN(ArtistEpoch.class)}; otherwise null. */
    @OneToMany public List<ArtistEpoch> artistEpochs;
}
