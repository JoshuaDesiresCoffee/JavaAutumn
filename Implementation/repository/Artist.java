package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

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

    /** Populated only when the query includes {@code JOIN(Artwork.class)}; otherwise null. */
    @OneToMany public List<Artwork> artworks;

    /** Populated only when the query includes {@code JOIN(ArtistEpoch.class)}; otherwise null. */
    @OneToMany public List<ArtistEpoch> artistEpochs;
}
