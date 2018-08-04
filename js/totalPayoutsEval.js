export const recalcUserGpVolume = async (db, criteria) => {
  await db.model('Users').find(criteria, (err, users) => {
    if (users) {
      users.forEach((user) => {
        let uGp = user.gp
        user.gp.totalPayout = recalcGpTotalPayout(uGp.goal, uGp.payout, uGp.lastYear, uGp.thisYear, uGp.threshold)
        let uVol = user.volume
        user.volume.totalPayout = recalcVolumeTotalPayout(uVol.goal, uVol.payout, uVol.lastYear, uVol.thisYear, uVol.threshold)
        user.save()
      })
    }
    if (err) {
      console.log('error ::: ', err)
    }
  })
}
export const recalcUserFpl = async (db, criteria) => {
  await db.model('Users').find(criteria, async (err, users) => {
    // get salesreps teamleads
    let teamleads = users.map((usr) => {
      return usr.role === 'teamLead' ? usr.customId : usr.teamLead[0].customId
    })

    // get unique teamleds
    teamleads = teamleads.filter((x, i, a) => a.indexOf(x) === i)

    // calculate team fpl  sum(fpl * thisYear) / sum (thisYear)
    let teams = {}

    for (let i = 0; i < teamleads.length; i++) {
      let teamfpl = await db.model('Users').find(
        {'teamLead': {$elemMatch: {'customId': teamleads[i]}}},
        {'fpl.individual': 1, 'volume.thisYear': 1})

      let sumFpl = teamfpl.reduce((a, b) => {
        return a + b.fpl.individual * b.volume.thisYear
      }, 0)

      let sumYear = teamfpl.reduce((a, b) => {
        return a + b.volume.thisYear
      }, 0)

      let teamFplTotal = (sumFpl / sumYear)
      teams[teamleads[i]] = teamFplTotal

      // update teanLead fpl total
      let user = await db.model('Users')
        .findOneAndUpdate({'customId': teamleads[i]}, {$set: {'fpl.team': teamFplTotal}}, {new: true})
    }

    // recalculate  fpl total for all edited users
    users.forEach(async (user) => {
      let uFpl = user.fpl
      let teamFplTotal = user.role === 'teamLead' ? teams[user.customId] : teams[user.teamLead[0].customId]

      // if (user.role === 'teamLead') user.teamLead = []

      user.fpl.team = teamFplTotal
      user.fpl.totalPayout = recalcFplTotalPayout(user.wholesaler.maxFpl, teamFplTotal, uFpl.payout, uFpl.goal)
      user.save()
    })
  })
}

export const recalcGpTotalPayout = (gpGoal, gpPayout, gpLast, gpThis, gpThreshold) => {
  const gpFactor = gpPayout / (gpGoal - (gpThreshold + (gpGoal - 100)))
  const lastYear = gpLast <= 0 ? 1 : (gpThis / gpLast) * 100
  const yearFactor = lastYear - gpGoal
  let result = (gpPayout + gpFactor * yearFactor).toFixed(2)
  return result > 0 ? result : 0
}

export const recalcVolumeTotalPayout = (volGoal, volPayout, volLast, volThis, volThreshold) => {
  const volFactor = volPayout / (volGoal - (volThreshold + (volGoal - 100)))
  const lastYear = volLast <= 0 ? 1 : (volThis / volLast) * 100
  const yearFactor = lastYear - volGoal
  let result = (volPayout + volFactor * yearFactor).toFixed(2)
  return result > 0 ? result : 0
}

export const recalcFplTotalPayout = (maxFpl, fplTeam, fplPayout, fplGoal) => {
  const fplFactor = fplPayout + (fplPayout / ((maxFpl - fplGoal) * 10)) * (fplGoal - fplTeam) * 10
  return maxFpl < fplTeam ? 0 : (fplFactor || 0)
}
