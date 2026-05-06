package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class UserRole {
    @Id
    public int id;
    public int userId;
    public int roleId;
}
