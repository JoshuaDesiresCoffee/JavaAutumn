package Implementation.repository;

import Autumn.orm.ForeignKey;
import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Artwork {
    @Id
    public int id;
    public String displayedAs;
    public String material;
    public String pictureUrl;
    @ForeignKey(table = Artist.class)
    public int artistId;
    @ForeignKey(table = Provenance.class)
    public int provenanceId;
}
