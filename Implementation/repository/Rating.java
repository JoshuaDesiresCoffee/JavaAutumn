package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class Rating {
    @Id
    public int id;
    public String displayedAs;
    public Stars stars;
    public User user;
    public Artwork artwork;
}
