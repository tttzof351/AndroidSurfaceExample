package com.example.surfaces.machines

interface Action

interface State

interface StateMachine<S : State, A : Action> {
    var state: S
    fun transition(action: A)
    fun send(action: A)
}