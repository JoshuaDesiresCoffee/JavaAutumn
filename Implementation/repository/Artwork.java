package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class Artwork {
    @Id
    public int id;
    public String displayedAs;
    public String material;
    public String pictureUrl;
    public int artistId;
    public int provenanceId;
}
