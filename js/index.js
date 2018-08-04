import connectToMongo from './db'
import dotenv from 'dotenv'
import BigQuery from '@google-cloud/bigquery'
import Storage from '@google-cloud/storage'
import {schema as Users} from './models/Users'
import json2csv from 'json2csv'

dotenv.config()

const bucketName = process.env.CLOUD_BUCKET
const projectId = process.env.projectId
const datasetId = process.env.datasetId
const tableId = process.env.tableId

// template file name for save via csv
const templCsv = 'exports/usersBQ.csv';

const bigquery = new BigQuery();

const storage = new Storage({
  projectId: projectId,
});

const setBucket = storage.bucket(bucketName);

const fieldNames = ['name', 'id', 'email', 'password', 'role']

const fields = ['name', 'customId', 'email', 'password', 'role', 'routeNumber']

// main function for export
exports.exportUsersBq = async function exportUsers(req, res) {

  const authHeader = req.header('Authorization')
  const checkHeader = process.env.SECRET_KEY

  const options = {
    schema: await getDataSchema(),
  };

  if (authHeader === checkHeader) {
    let mongoDB = connectToMongo();
      let users; 
      
      // get users from mongo
      await mongoDB.model('Users').find({}, (err, task) => {
        if (err){
          res.send({message: 'mongo error', error: err.message})
        }else{
          users = task
        }
      });
      
      // get date for save to DB
      let nowDate = new Date();
      let nowDateStr = nowDate.toISOString().split('.')[0];

      // data prepare
      users = users.map((user) => {
        /* 1 - join routes */
        user = user.toObject()
        user['routeNumber'] = user['routeNumber'].join()
        /* 2 - remove array from managers  */
        user['manager'] = (user['manager'][0]) ? user['manager'][0] : {}
        user['teamLead'] = (user['teamLead'][0]) ? user['teamLead'][0] : {}
        user['gbq_insert_date'] = nowDateStr;
        return user
      })

      // 
      let rows = users.map((obj) => {
        // TODO set users field to dataschema
        return replaceData(obj);
      })

      // upload csv to gcs which will load to BQ
      const csv = json2csv({data: users, fields, fieldNames, hasCSVColumnTitle: false})
      const saveFile = setBucket.file(templCsv)
      
      // save data to csv file into cloud storage
      await saveFile.save(csv)
      await saveFile.makePublic()

      // delete and create new table in BQ
      await deleteTable()
      await createTable(options)

      // load csv file to BQ table
      let result = await loadFileFromGCS(templCsv)
      console.log('load result ::', result);
      
      // send response
      res.send({message: 'Export of users done successfully'})
        
  } else {
    res.send({message: 'Unauthorized'})
  }
}

// replace type from mongo to type in BQ
function typeReplace(type) {
  switch (type) {
    case 'number':
    case 'Number':
      return 'float'
      break
    case 'string':
    case 'String':
      return 'string'
      break
    default:
      return type
      break
  }
}

// get schema of mongo
// get field type from schema and associate to BQ type 
function getDataSchema() {
  let dataSchema = {}
  let defaultType = {type: 'String'}
  let i = 0;
  while (i < fields.length) {

    let fName = fields[i].split('.');
    let field;

    if (Users.obj[fName[0]]) {
      if (Users.obj[fName[0]][0]) {

        if (fName.length === 1) {
          field = Users.obj[fName[0]][0];
        } else {
          field = Users.obj[fName[0]][0][fName[1]] ? Users.obj[fName[0]][0][fName[1]] : defaultType;
        }
      } else {
        if (fName.length === 1) {
          field = Users.obj[fName[0]];
        } else {
          field = Users.obj[fName[0]][fName[1]] ? Users.obj[fName[0]][fName[1]] : defaultType;
        }
      }
    } else {
      field = defaultType;
    }

    let fieldT = field ? (typeof field.type == 'function' ? field.type.name : (field.type ? field.type : field.name)) : 'String';
    let fieldN = fieldNames[i]
    fieldT = typeReplace(fieldT)
    dataSchema[fieldN] = fieldT.toLowerCase();
    i++;
  }
  dataSchema = JSON.stringify(dataSchema, null, '').replace(new RegExp('"', 'g'), '').replace('{', '').replace('}', '')

  return dataSchema;
}

// replace data with new filed names if use json load to BQ
function replaceData(object) {
  let i = 0;
  let backObject = {};
  while (i < fields.length) {
    let fName = fields[i].split('.');
    if (fName.length === 1) {
      backObject[fieldNames[i]] = object[fName[0]]
    } else {
      backObject[fieldNames[i]] = object[fName[0]][fName[1]]
    }
    i++
  }
  return backObject;
}

// delete table in BQ
async function deleteTable() {
  try{
    await bigquery
    .dataset(datasetId)
    .table(tableId)
    .delete()
  }catch (err){
    console.log('delete error :: ', err.message)
  }
  console.log(`Table ${tableId} deleted.`);
}

// create table in BQ
async function createTable(options) {
  try{
    let result = await bigquery
      .dataset(datasetId)
      .createTable(tableId, options)
    const table = result[0];
    console.log(`Table ${table.id} created.`);
    return result;
  } catch(err){
    console.log('create table error', err)
    return err;
  }
}

// insert from GCS file
async function loadFileFromGCS(filename) {
  let job;

  try{
    let file = storage.bucket(bucketName).file(filename);

    // load file to BQ table
    let results = await bigquery
      .dataset(datasetId)
      .table(tableId)
      .load(file)
      
    job = results[0];
    console.log(`Job ${job.id} started.`);

    // check error
    const errors = job.status.errors;
    if (errors && errors.length > 0) {
      return errors
    } else {
      console.log(`Job ${job.id} completed.`);  
    }

    // response
    return 'finished';
  } catch (err){
    console.log('data export error', err);
    return err;
  }
}
