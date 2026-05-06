package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class Rating {
    @Id
    public int id;
    public String displayedAs;
    public int starsId;
    public int userId;
    public int artworkId;
}
