import log from '../../utils/log'
import inspector from 'schema-inspector'
import {generatePass} from '../../utils/generateDefaultPass'
import {validation, sanitizationUserDetails as sanitization, sanitizationUpdateUser} from './sharedCode/userSchema'
import {getUsers} from './sharedCode/getUsers'
import mongoose from 'mongoose'
import {recalcUserGpVolume, recalcUserFpl} from './sharedCode/totalPayoutsEval'

export default async({payload = {}, socket, emitAction, db, sessionData, connectedUsers}) => {
  
  // get request parametesr
  const {gpGoal, gpPayout, gpThreshold,
        ceGoal, cePayout, ceThreshold,
        fplGoal, fplPayout, teamLead, usersList} = payload;

  let message;
  let updateQuery = {}

  try{
    // get users id from request
    let usersId = usersList.map((user) => {
      return user._id;
    })

    // set criteria for query
    var criteria = {
      _id:{ $in: usersId}
    };

    // set filed value to update
    let set = {}
    if (gpGoal) set['gp.goal'] = gpGoal;
    if (gpPayout) set['gp.payout'] = gpPayout;
    if (gpThreshold) set['gp.threshold'] = gpThreshold;
    
    if (ceGoal )set['volume.goal'] = ceGoal;
    if (cePayout) set['volume.payout'] = cePayout;
    if (ceThreshold) set['volume.threshold'] = ceThreshold;

    if (teamLead) {
      let teamLeadEdit = await db.model('Users').find({customId: teamLead}, {name:1, customId:1});
      console.log('teamlead edit :: ', teamLeadEdit);
      set['teamLead'] = teamLeadEdit;
    }

    // update all users with criteria about set parameter
    await db.model('Users').update(criteria, {$set : set}, {multi:true});
    await recalcUserGpVolume(db, criteria)

    // if fpl edit then edit for all users in teamlead
    if (fplGoal || fplPayout){
      let usersTeamleadId;
      if (teamLead){
        /* set criteris teamlead if i present in request */
        usersTeamleadId = [teamLead];
      }else{

        /* get teamleads from users list in request  */
        usersTeamleadId = usersList.map((user) => {
          return user.teamLead[0].customId;
        })

        /* get unique list array */
        usersTeamleadId = usersTeamleadId.filter((x, i, a) => a.indexOf(x) === i);
      }

      updateQuery = {
        $or : [
          {'teamLead': {$elemMatch: {'customId': {$in: usersTeamleadId} } } },
          {'role':'teamLead', 'customId': {$in : usersTeamleadId} } ]
      }

      let setFpl = {}
      if (fplGoal) setFpl['fpl.goal'] = fplGoal;
      if (fplPayout) setFpl['fpl.payout'] = fplPayout;
      let totalFpl

      await db.model('Users').update(updateQuery, {$set : setFpl}, {multi:true})  
      await recalcUserFpl(db, criteria)
    }

    message = 'User was updated'
    emitAction('HIDE_DIALOG')
    emitAction('SNACK', {message})
  } catch (err) {
    log.err(err)
    message = 'Something went wrong'
    emitAction('SNACK', {message})
  }
  
}
