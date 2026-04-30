package Implementation.repository;

import Autumn.orm.ForeignKey;
import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Rating {
    @Id
    public int id;
    public String displayedAs;
    @ForeignKey(table = Stars.class)
    public int starsId;
    @ForeignKey(table = User.class)
    public int userId;
    @ForeignKey(table = Artwork.class)
    public int artworkId;
}

