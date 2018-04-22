# Agent Madoff @ Automated Negotiation Agents Competition 2017 - Diplomacy Challenge

Detailed paper on my agent: https://tinyurl.com/agentMadoff.

The Automated Negotiation Agents Competition (ANAC) is one of the competitions which fuels research interest in developing practical, state-of-the-art agents that can negotiate under various circumstances. ANAC 2017 introduces a new negotiation league named as “Negotiation Strategies for the Diplomacy Strategy Game”, which is a much more surreal condition which is closer to the human negotiation environment, and with no doubt the complexity should increase significantly.

The Diplomacy Game

Diplomacy is a strategy game for 7 players, representing one of the 7 “Great Powers in Europe” in the years prior to World War I, namely England, France, Germany, Italy, Austria, Turkey and Russia. Each player has a number of armies and fleets positioned on the map of Europe, and the goal is to conquer half of the “Supply Centers” across Europe. 

It is extremely hard for one single player to win the Diplomacy Game without gaining support from the other players, hence negotiation plays a very important role in this game to form alliances among players and agree on certain commitments and promises in order to reach each player’s own objective.

About Agent Madoff

Negotiation is done using the BAsic eNvironment for Diplomacy playing Automated Negotiating Agents (BANDANA) framework, which is a Java based framework by Dave de Jonge. 

The design architecture of Agent Madoff mainly uses an evaluation algorithm based on 2 metrics: hostility and neediness. We choose to cooperate or not with an opponent based on how hostile is he on us, and also how much do we need him.