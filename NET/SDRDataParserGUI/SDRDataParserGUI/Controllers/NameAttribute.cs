using System;

[AttributeUsage(AttributeTargets.All,
    AllowMultiple = false)]
public class NameAttribute : System.Attribute
    {
    public string name { get; set; }
    public NameAttribute( string name)
    {
        this.name = name;
    }
   }
