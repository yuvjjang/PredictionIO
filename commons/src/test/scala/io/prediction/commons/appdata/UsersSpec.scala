package io.prediction.commons.appdata

import io.prediction.commons.Spec

import org.specs2._
import org.specs2.specification.Step

import com.mongodb.casbah.Imports._
import com.github.nscala_time.time.Imports._

class UsersSpec extends Specification {
  def is = s2"""

  PredictionIO App Data Users Specification

    Users can be implemented by:
    - MongoUsers ${mongoUsers}

  """

  def mongoUsers = s2"""

    MongoUsers should
    - behave like any Users implementation ${users(newMongoUsers)}
    - (database cleanup) ${Step(Spec.mongoClient(mongoDbName).dropDatabase())}

  """

  def users(users: Users) = s2"""

    inserting and getting a user ${insert(users)}
    getting all users by App ID ${getByAppid(users)}
    updating a user ${update(users)}
    deleting a user ${delete(users)}
    deleting users by appid ${deleteByAppid(users)}
    count users by appid ${countByAppid(users)}

  """

  val mongoDbName = "predictionio_appdata_mongousers_test"
  def newMongoUsers = new mongodb.MongoUsers(Spec.mongoClient(mongoDbName))

  def insert(users: Users) = {
    val appid = 0
    val id1 = "insert1"
    val user1 = User(
      id = id1,
      appid = appid,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = None,
      attributes = Some(Map("foo" -> "bar"))
    )
    val id2 = "insert2"
    val user2 = User(
      id = id2,
      appid = appid,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = Some(true),
      attributes = None
    )
    users.insert(user1)
    users.insert(user2)
    users.get(appid, id1) must beSome(user1) and
      (users.get(appid, id2) must beSome(user2))

  }

  def getByAppid(users: Users) = {
    val appid = 3
    val ourUsers = List(
      User(
        id = "getByAppid-3",
        appid = appid,
        ct = DateTime.now,
        latlng = Some((47.8948, -29.79783)),
        inactive = None,
        attributes = Some(Map("foo" -> "bar"))),
      User(
        id = "getByAppid-2",
        appid = appid,
        ct = DateTime.now,
        latlng = Some((47.8948, -29.79783)),
        inactive = None,
        attributes = Some(Map("foo" -> "bar"))),
      User(
        id = "getByAppid-1",
        appid = appid,
        ct = DateTime.now,
        latlng = Some((47.8948, -29.79783)),
        inactive = None,
        attributes = Some(Map("foo" -> "bar"))))
    ourUsers foreach { users.insert(_) }
    val getUsers = users.getByAppid(appid).toList.sortWith((x, y) => x.id < y.id)
    getUsers(0) must beEqualTo(ourUsers(2)) and
      (getUsers(1) must beEqualTo(ourUsers(1))) and
      (getUsers(2) must beEqualTo(ourUsers(0)))
  }

  def update(users: Users) = {
    val appid = 1
    val id = "update"
    val user = User(
      id = id,
      appid = appid,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = None,
      attributes = Some(Map("foo" -> "bar"))
    )
    val updatedUser = user.copy(
      latlng = None,
      inactive = Some(true),
      attributes = Some(Map("dead" -> "beef"))
    )
    users.insert(user)
    users.update(updatedUser)
    users.get(appid, id) must beSome(updatedUser)
  }

  def delete(users: Users) = {
    val appid = 2
    val id = "delete"
    val user = User(
      id = id,
      appid = appid,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = None,
      attributes = Some(Map("foo" -> "bar"))
    )
    users.delete(user)
    users.get(appid, id) must beNone
  }

  def deleteByAppid(users: Users) = {
    // insert a few users with appid1 and a few users with appid2.
    // delete all users of appid1.
    // users of appid1 should be deleted and users of appid2 should still exist.
    // delete all users of appid2
    // users of appid2 should be deleted

    val appid1 = 10
    val appid2 = 11

    val ida = "deleteByAppid-ida"
    val idb = "deleteByAppid-idb"
    val idc = "deleteByAppid-idc"

    val user1a = User(
      id = ida,
      appid = appid1,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = None,
      attributes = Some(Map("c" -> "d"))
    )
    val user1b = user1a.copy(
      id = idb,
      attributes = Some(Map("e" -> "f"))
    )
    val user1c = user1a.copy(
      id = idc,
      attributes = Some(Map("g" -> "h"))
    )

    val user2a = user1a.copy(
      appid = appid2
    )
    val user2b = user1b.copy(
      appid = appid2
    )
    val user2c = user1c.copy(
      appid = appid2
    )

    users.insert(user1a)
    users.insert(user1b)
    users.insert(user1c)
    users.insert(user2a)
    users.insert(user2b)
    users.insert(user2c)

    val g1_1a = users.get(appid1, ida)
    val g1_1b = users.get(appid1, idb)
    val g1_1c = users.get(appid1, idc)

    val g1_2a = users.get(appid2, ida)
    val g1_2b = users.get(appid2, idb)
    val g1_2c = users.get(appid2, idc)

    users.deleteByAppid(appid1)

    val g2_1a = users.get(appid1, ida)
    val g2_1b = users.get(appid1, idb)
    val g2_1c = users.get(appid1, idc)

    val g2_2a = users.get(appid2, ida)
    val g2_2b = users.get(appid2, idb)
    val g2_2c = users.get(appid2, idc)

    users.deleteByAppid(appid2)

    val g3_2a = users.get(appid2, ida)
    val g3_2b = users.get(appid2, idb)
    val g3_2c = users.get(appid2, idc)

    (g1_1a, g1_1b, g1_1c) must be_==((Some(user1a), Some(user1b), Some(user1c))) and
      ((g1_2a, g1_2b, g1_2c) must be_==((Some(user2a), Some(user2b), Some(user2c)))) and
      ((g2_1a, g2_1b, g2_1c) must be_==((None, None, None))) and
      ((g2_2a, g2_2b, g2_2c) must be_==((Some(user2a), Some(user2b), Some(user2c)))) and
      ((g3_2a, g3_2b, g3_2c) must be_==((None, None, None)))

  }

  def countByAppid(users: Users) = {
    val appid1 = 23
    val appid2 = 24
    val appid3 = 25

    val ida = "countByAppid-ida"
    val idb = "countByAppid-idb"

    val user1a = User(
      id = ida,
      appid = appid1,
      ct = DateTime.now,
      latlng = Some((47.8948, -29.79783)),
      inactive = None,
      attributes = Some(Map("c" -> "d"))
    )
    val user1b = user1a.copy(
      id = idb,
      appid = appid1,
      attributes = Some(Map("e" -> "f"))
    )
    val user2a = user1a.copy(
      appid = appid2
    )

    users.insert(user1a)
    users.insert(user1b)
    users.insert(user2a)

    users.countByAppid(appid1) must be_==(2) and
      (users.countByAppid(appid2) must be_==(1)) and
      (users.countByAppid(appid3) must be_==(0))
  }
}
