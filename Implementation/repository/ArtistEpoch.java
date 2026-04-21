package Implementation.repository;

import Autumn.orm.ForeignKey;
import Autumn.orm.Id;
import Autumn.orm.Table;

@Table(name = "artist_epoch")
public class ArtistEpoch {
    @Id
    public int id;
    @ForeignKey(table = Artist.class)
    public int artistId;
    @ForeignKey(table = Epoch.class)
    public int epochId;
}
