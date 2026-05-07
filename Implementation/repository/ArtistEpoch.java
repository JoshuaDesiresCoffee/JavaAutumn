package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table(name = "artist_epoch")
public class ArtistEpoch {
    @Id
    public int id;
    public Artist artist;
    public Epoch epoch;
}
